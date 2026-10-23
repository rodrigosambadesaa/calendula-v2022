/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.healthcareprovider.util

import android.content.Context
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.drugdb.DBRegistry
import es.usc.citius.servando.calendula.events.PersistenceEvents
import es.usc.citius.servando.calendula.persistence.Medicine
import es.usc.citius.servando.calendula.persistence.Patient
import es.usc.citius.servando.calendula.persistence.Schedule
import es.usc.citius.servando.calendula.scheduling.Agenda
import es.usc.citius.servando.calendula.healthcareprovider.jobs.UpdateSummary
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedVO
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageVO
import es.usc.citius.servando.calendula.healthcareprovider.notifications.ActiveMedNotification
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType
import es.usc.citius.servando.calendula.healthcareprovider.util.ScheduleComparator.compare
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.PreferenceKeys
import es.usc.citius.servando.calendula.util.PreferenceUtils
import es.usc.citius.servando.calendula.util.ProviderRevisionGuard
import org.joda.time.DateTime



object ActiveMedUpdater {

    private const val TAG = "ActiveMedUpdater"

    /** Pure comparison to avoid overlooking an updated server display name. */
    internal fun displayNameChanged(received: String?, stored: String?): Boolean =
        received != stored

    /** Verify imported owners before any patient-specific DAO mutation. */
    internal fun incomingOwnersMatchActivePatient(
        activePatientId: Long?,
        incomingPatientIds: Collection<Long?>
    ): Boolean = activePatientId != null && activePatientId > 0L &&
        incomingPatientIds.all { it == activePatientId }

    /** Ambiguous duplicate provider codes within one snapshot must fail closed. */
    internal fun hasUniqueUsableCodes(codes: Collection<String?>): Boolean =
        codes.all { !it.isNullOrBlank() } && codes.size == codes.toSet().size

    /**
     * Several local schedules/medicines can legitimately link to one official
     * medication. Validate every linked owner rather than silently ignoring
     * all but the first or allowing another patient's links to be mutated.
     */
    internal fun allLinksBelongToPatient(
        expectedPatientId: Long?, linkedPatientIds: Collection<Long?>
    ): Boolean = expectedPatientId != null && expectedPatientId > 0L &&
        linkedPatientIds.all { it == expectedPatientId }

    /**
     * Validate *all* linked provider medicines before touching data. An
     * incoming update may change schedules just as a retirement would.
     * Cross-owned/ownerless records still require manual repair.
     */
    private fun validateLinkedProviderOwners(patientId: Long) {
        DB.healthcareProviderDB().activeMeds()
            .findBy(ActiveMedEntity.COLUMN_PATIENT, patientId)
            .forEach { stored ->
                check(stored.patient.id == patientId) {
                    "Provider medicine ownership mismatch before retirement"
                }
                val schedules = DB.schedules().findBy(Schedule.BOUND_TO, stored.id)
                val medicines = DB.medicines().findBy(Medicine.COLUMN_ACTIVE_MED_ID, stored.id)
                check(allLinksBelongToPatient(patientId, schedules.map { it.patient?.id }) &&
                      allLinksBelongToPatient(patientId, medicines.map { it.patient?.id }) &&
                      schedules.all { it.medicine == null || it.medicine.patient?.id == patientId }) {
                    "Cross-patient or ownerless provider medicine links"
                }
            }
    }

    /**
     * Called when active med list has changed
     *
     * @param newHash the new hash
     * @param meds    the meds
     */
    fun onNewMeds(
        newHash: String,
        activeMedication: List<ActiveMedVO>,
        ctx: Context,
        notify: Boolean = true
    ) {
        // Medication display names and remote hashes are sensitive clinical
        // context: log a count only, never identifiers or prescription data.
        LogUtil.d(TAG, "onNewMeds: received medication count=${activeMedication.size}")
        val patient = DB.patients().getActive(ctx)
            ?: throw IllegalStateException("Active patient is required for provider import")
        // Reject an unusable owner before any SQLite mutation, not after commit.
        val revisionOwner = ProviderRevisionGuard.ownerKey(patient.id, patient.code)
            ?: throw IllegalStateException("Provider revision requires persisted patient identity")
        val incomingOwners = activeMedication.map { it.patient?.id }
        // All subsequent medication lookups are scoped by the persisted
        // patient and code. Another patient's identical code is legitimate.
        // Duplicate codes *within this snapshot* are ambiguous and rejected.
        if (!hasUniqueUsableCodes(activeMedication.map { it.code })) {
            throw IllegalStateException("Provider medication codes missing or duplicated")
        }
        if (!incomingOwnersMatchActivePatient(patient.id, incomingOwners)) {
            LogUtil.w(TAG, "Provider medication update rejected due to patient ownership mismatch")
            throw IllegalStateException("Provider medication patient ownership mismatch")
        }
        val summary = UpdateSummary()
        // update active med list
        // Even a verified, complete empty FHIR snapshot is authoritative:
        // reconcile prior provider medicines instead of acknowledging its
        // revision while silently retaining stale active prescriptions.
        // The FHIR parser must reject malformed/partial snapshots *before*
        // this function is reached. Cleanup remains patient-scoped.
        DB.transaction {
            // A profile can change after FHIR parsing/preflight. Do not
            // begin mutating medicines under a stale active patient.
            check(DB.patients().getActive(ctx)?.id == patient.id) {
                "Active patient changed during provider import"
            }
            // Link ownership must be checked inside the same transaction as
            // the eventual retirement, not against a stale preflight snapshot.
            validateLinkedProviderOwners(patient.id)
            activeMedication.forEach {
                // Abort and roll back if a profile switch is observed
                // between imported items, rather than continuing writes.
                check(DB.patients().getActive(ctx)?.id == patient.id) {
                    "Active patient changed during provider import"
                }
                // always update dispensation info if med is stored
                if (isStored(it)) {
                    updateActiveMedDispensation(it)
                }
                // update med and set pending review if something changed
                if (shouldBeUpdated(it)) {
                    LogUtil.d(TAG, "onNewMeds: Updating a medication")
                    val updatedMed = updateActiveMed(it, summary)
                    setPendingReview(ctx, updatedMed)
                }
                // or create a new active med if it not exists
                else if (shouldBeCreated(it)) {
                    LogUtil.d(TAG, "onNewMeds: Creating a medication")
                    addToKit(createActiveMed(it, summary), patient)
                }
            }
            // Cleanup can retire schedules: never run it after observing
            // an intervening profile change.
            check(DB.patients().getActive(ctx)?.id == patient.id) {
                "Active patient changed before provider cleanup"
            }
            cleanOldActiveMeds(ctx, patient.id, summary, activeMedication)
        }
        // Never cancel OS alarm tokens before SQLite commits: a rollback
        // could restore medication/schedules while leaving alarms revoked.
        // Always retry cleanup, even if the previous attempt committed SQL
        // and failed before writing the remote revision. This also recovers
        // outstanding unlinked reminders left by an interrupted sync.
        Agenda.instance().cleanRemindersForPatient(ctx, patient)
        // Even an empty snapshot must not acknowledge a remote revision for
        // a patient who was switched away during the import.
        check(DB.patients().getActive(ctx)?.id == patient.id) {
            "Active patient changed before provider revision acknowledgement"
        }
        // update the stored hash
        // Couple revision and its authoritative patient in one preference edit;
        // a legacy unowned hash must never authorize another profile's 304.
        PreferenceUtils.edit()
            .putString(PreferenceKeys.REMOTE_LAST_HASH_OWNER.key(), revisionOwner)
            .putString(PreferenceKeys.REMOTE_LAST_HASH.key(), newHash)
            .apply()
        // show a notification if necessary
        if (notify && summary.hasChanges()) {
            ActiveMedNotification.show(ctx, patient, summary)
        }
        CalendulaApp.eventBus()
            .post(PersistenceEvents.ModelCreateOrUpdateEvent(ActiveMedEntity::class.java))
    }

    private fun cleanOldActiveMeds(
        c: Context,
        patientId: Long?,
        summary: UpdateSummary,
        meds: List<ActiveMedVO>
    ) {
        for (e in DB.healthcareProviderDB().activeMeds().findBy(ActiveMedEntity.COLUMN_PATIENT, patientId)) {
            val isInActiveMedList = meds.any { it.code == e.code }
            if (!isInActiveMedList) {
                LogUtil.d(TAG, "cleanOldActiveMeds: Removing a medication")
                summary.addDeleted(e)
                onRemovedFromActiveMeds(c, e)
            }
        }
    }

    private fun addToKit(entity: ActiveMedEntity, p: Patient) {
        // If the medicine is in medkit we just relink it (set activeMedId).
        // If not, it must be created.
        val m: Medicine = when {
            entity.type == ActiveMedType.DCPF && entity.homogeneousGroup != null ->
                DB.medicines().findByGroupAndPatient(entity.code, p)
                        ?: Medicine.fromHomogeneousGroup(entity.homogeneousGroup)

            entity.type == ActiveMedType.NATIONAL_CODE && entity.prescription != null ->
                DB.medicines().findByCnAndPatient(entity.code, p)
                        ?: Medicine.fromPrescription(entity.prescription)

            else -> createMedicineFromActiveMed(entity)
        }
        m.apply {
            activeMedId = entity.id
            patient = p
        }
        DB.medicines().save(m)
    }

    private fun isStored(med: ActiveMedVO): Boolean {
        return DB.healthcareProviderDB().activeMeds().findByCodeForPatient(med.code, med.patient) != null
    }

    private fun shouldBeCreated(med: ActiveMedVO): Boolean {
        return !isStored(med)
    }

    private fun shouldBeUpdated(med: ActiveMedVO): Boolean {
        val stored = DB.healthcareProviderDB().activeMeds().findByCodeForPatient(med.code, med.patient)
        var changed = false
        stored?.let {
            val vo = ActiveMedVO.forEntity(stored)
            changed = changedSinceLastUpdate(med, vo)
        }

        return changed
    }

    private fun createActiveMed(
        med: ActiveMedVO,
        summary: UpdateSummary
    ): ActiveMedEntity {

        val dispensationDao = DB.healthcareProviderDB().dispensationInfo()
        val entryDao = DB.healthcareProviderDB().dosageEntries()

        val activeMed = med.backingEntity
        val dosageEntity = med.dosage.entity()

        // save active med
        activeMed.setLastUpdated(DateTime.now(), false)
        activeMed.state = ActiveMedEntity.ActiveMedState.ACTIVE
        DB.healthcareProviderDB().activeMeds().save(activeMed)

        // save dosage
        dosageEntity.activeMed = activeMed
        DB.healthcareProviderDB().dosages().save(dosageEntity)

        // update dosage reference
        activeMed.dosage = dosageEntity
        DB.healthcareProviderDB().activeMeds().save(activeMed)

        // save dosage entries
        for (entryVO in med.dosage.entries) {
            val entry = entryVO.entity()
            entry.dosage = dosageEntity
            entryDao.save(entry)
        }
        // save dispensation info
        val dispensationInfo = med.backingEntity.dispensationInfo
        if (dispensationInfo != null) {
            for (dispensationInfoEntity in dispensationInfo) {
                dispensationInfoEntity.activeMed = activeMed
                dispensationDao.save(dispensationInfoEntity)
            }
        }
        summary.addCreated(activeMed)
        return activeMed
    }

    private fun updateActiveMed(
        med: ActiveMedVO,
        summary: UpdateSummary
    ): ActiveMedEntity {

        val storedActiveMed =
            DB.healthcareProviderDB().activeMeds().findByCodeForPatient(med.code, med.patient)
        storedActiveMed!!.dosage.entries.map { DB.healthcareProviderDB().dosageEntries().remove(it) }
        DB.healthcareProviderDB().dosages().remove(storedActiveMed.dosage)

        storedActiveMed.setLastUpdated(DateTime.now(), false)
        storedActiveMed.state = ActiveMedEntity.ActiveMedState.ACTIVE
        storedActiveMed.defaultDisplay = med.defaultDisplay;
        storedActiveMed.validityStart = med.validityStart;
        storedActiveMed.validityEnd = med.validityEnd;
        storedActiveMed.visualizationType = med.visualizationType;
        // save active med

        // save dosage
        val dosageEntity = med.dosage.entity()
        dosageEntity.activeMed = storedActiveMed
        DB.healthcareProviderDB().dosages().save(dosageEntity)
        // update dosage reference
        storedActiveMed.dosage = dosageEntity
        DB.healthcareProviderDB().activeMeds().save(storedActiveMed)

        // save dosage entries
        val entryDao = DB.healthcareProviderDB().dosageEntries()
        for (entryVO in med.dosage.entries) {
            val entry = entryVO.entity()
            if (entry.id == null) {
                entry.dosage = dosageEntity
                entryDao.save(entry)
            }
        }
        summary.addUpdated(storedActiveMed)
        return storedActiveMed
    }

    private fun updateActiveMedDispensation(med: ActiveMedVO): ActiveMedEntity {
        val storedActiveMed =
            DB.healthcareProviderDB().activeMeds().findByCodeForPatient(med.code, med.patient)
        storedActiveMed.dispensationInfo.map { DB.healthcareProviderDB().dispensationInfo().remove(it) }
        // save dispensation info
        val dispensationInfo = med.backingEntity.dispensationInfo
        if (dispensationInfo != null) {
            val dispensationDao = DB.healthcareProviderDB().dispensationInfo()
            for (dispensationInfoEntity in dispensationInfo) {
                dispensationInfoEntity.activeMed = storedActiveMed
                dispensationDao.save(dispensationInfoEntity)
            }
        }
        return storedActiveMed
    }


    private fun onRemovedFromActiveMeds(c: Context, activeMed: ActiveMedEntity) {
        // Set schedule state to PENDING_REVIEW
        DB.schedules().findBy(Schedule.BOUND_TO, activeMed.id).forEach {
            it.addState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_DELETE)
            it.removeState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)
            it.removeState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)
            it.activeMedId = null
            DB.schedules().save(it)
            DB.eventInstances().removeByRefAndTime(it.id, DateTime.now())
        }
        // Remove active med reference from linked medicines
        DB.medicines().findBy(Medicine.COLUMN_ACTIVE_MED_ID, activeMed.id).forEach {
            it.activeMedId = null
            DB.medicines().save(it)
        }
        // remove dosage entries
        activeMed.dosage.entries.forEach { DB.healthcareProviderDB().dosageEntries().remove(it) }
        // remove dispensation info
        activeMed.dispensationInfo.forEach { DB.healthcareProviderDB().dispensationInfo().remove(it) }
        // remove dosage
        DB.healthcareProviderDB().dosages().remove(activeMed.dosage)
        // remove active med
        DB.healthcareProviderDB().activeMeds().remove(activeMed)

    }

    internal fun setPendingReview(c: Context, activeMed: ActiveMedEntity) {
        // Set schedule state to PENDING_REVIEW
        DB.schedules().findBy(Schedule.BOUND_TO, activeMed.id).forEach {
            it.addState(Schedule.ScheduleState.PENDING_REVIEW_AFTER_UPDATE)
            val vo = ActiveMedVO.forEntity(activeMed);
            val dosage: DosageVO = vo.dosage
            if(dosage.type == DosageType.AS_NEEDED || dosage.type == DosageType.NONE) {
                it.removeState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)
                it.removeState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)
            }
            else {
                it.addState(Schedule.ScheduleState.CREATED_FROM_OFFICIAL)
                val changes = compare(vo, it)
                if (changes.isEmpty()) {
                    it.removeState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)
                }
                else {
                    it.addState(Schedule.ScheduleState.DIFFERS_FROM_OFFICIAL)
                }
            }
            DB.schedules().save(it)
            DB.eventInstances().removeByRefAndTime(it.id, DateTime.now())
        }
    }

    /**
     * Active meds should be updated (and user notified) if anything from remote, other than the dispensation info, changes
     *
     * @return `true` if the med has changed, `false` if not
     */
    private fun changedSinceLastUpdate(
        activeMed: ActiveMedVO,
        candidate: ActiveMedVO
    ): Boolean {

        // checking the code is not needed because the entity is retrieved by code
        if (activeMed.activeMedType != candidate.activeMedType) {
            LogUtil.d(TAG, "ActiveMedType changed")
            return true
        }
        if (displayNameChanged(activeMed.defaultDisplay, candidate.defaultDisplay)) {
            LogUtil.d(TAG, "DefaultDisplay changed")
            return true
        }
        // compare dosage entities
        if (activeMed.dosage.entity() != candidate.dosage.entity()) {
            LogUtil.d(TAG, "Dosage changed")
            return true
        }
        if (activeMed.validityStart != candidate.validityStart) {
            LogUtil.d(TAG, "Validity start changed")
            return true
        }
        if (activeMed.validityEnd != candidate.validityEnd) {
            LogUtil.d(TAG, "Validity end changed")
            return true
        }
        if (activeMed.visualizationType != candidate.visualizationType) {
            LogUtil.d(TAG, "Extra info changed")
            return true
        }
        return false
    }


    /**
     * This is used for medicines which are not present in our DB.
     */
    private fun createMedicineFromActiveMed(a: ActiveMedEntity): Medicine = Medicine().apply {
        name = a.defaultDisplay
        presentation = DBRegistry.instance().current()
            .expectedPresentation(a.defaultDisplay, a.defaultDisplay)
        when (a.type) {
            ActiveMedType.DCPF -> homogeneousGroup = a.code
            ActiveMedType.NATIONAL_CODE -> cn = a.code
        }
        database = null
    }

}