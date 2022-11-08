package es.usc.citius.servando.calendula.healthcareprovider.remote;

import org.hl7.fhir.dstu3.model.Bundle;

import es.usc.citius.servando.calendula.healthcareprovider.fhir.ResponseVO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface ActiveMedRemoteService {
    @Headers({
            "Content-Type: application/json"
    })
    @POST
    Call<ResponseVO> getDispensationPlan(@Url String url, @Body Bundle request);
}
