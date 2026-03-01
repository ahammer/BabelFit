package ca.adamhammer.babelfit.samples.customersupport.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CompanyTemplate(
    val title: String,
    val company: String,
    val product: String,
    val version: String,
    val lastUpdated: String,
    val contact: Contact,
    val sections: Map<String, Section>,
    val templates: Templates,
    val companyInfo: CompanyInfo
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun loadFromResource(): CompanyTemplate {
            val text = CompanyTemplate::class.java
                .getResourceAsStream("/company_template.json")
                ?.bufferedReader()?.readText()
            check(text != null) { "company_template.json not found on classpath" }
            return json.decodeFromString(serializer(), text)
        }
    }
}

@Serializable
data class Contact(
    val supportEmail: String,
    val supportPhone: String,
    val hours: String,
    val supportPortal: String
)

@Serializable
data class Section(
    val title: String,
    val content: String
)

@Serializable
data class Templates(
    @kotlinx.serialization.SerialName("rma_request")
    val rmaRequest: String,
    @kotlinx.serialization.SerialName("support_response")
    val supportResponse: String
)

@Serializable
data class CompanyInfo(
    val founded: Int,
    val mission: String,
    val primaryCustomers: String,
    val models: List<String>,
    val salesChannels: List<String>
)
