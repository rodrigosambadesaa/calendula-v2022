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

package es.usc.citius.servando.calendula.healthcareprovider.remote.userInfo


import es.usc.citius.servando.calendula.util.GsonUtil
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.io.IOException
import java.lang.reflect.Type

class UserInfo {

    // We are only going to take care of first and last name
    // attributes from the userInfo endpoint response

    var id: String? = null
    var firstName: String? = null
    var lastName: String? = null

    class UserInfoConverterFactory : Converter.Factory() {
        private val userInfoConverter =
            UserInfoConverter()

        override fun responseBodyConverter(
            type: Type?,
            annotations: Array<Annotation>?,
            retrofit: Retrofit?
        ): Converter<ResponseBody, *>? {
            return userInfoConverter
        }
    }

    class UserInfoConverter : Converter<ResponseBody, UserInfo> {
        @Throws(IOException::class)
        override fun convert(value: ResponseBody): UserInfo {
            return GsonUtil.get().fromJson(value.string(), UserInfo::class.java)
        }
    }

}
