package com.example.pitwise.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            // Throw exception on non-2xx responses — critical for auth error handling
            expectSuccess = true

            install(ContentNegotiation) {
                json(json)
            }

            // Extract human-readable error messages from Supabase GoTrue JSON errors
            HttpResponseValidator {
                handleResponseExceptionWithRequest { exception, _ ->
                    val clientException = exception as? io.ktor.client.plugins.ClientRequestException
                        ?: return@handleResponseExceptionWithRequest

                    val responseBody = clientException.response.bodyAsText()

                    // GoTrue returns {"error":"...","msg":"...","error_description":"..."} or {"message":"..."}
                    val message = try {
                        val jsonObj = json.parseToJsonElement(responseBody)
                        val obj = jsonObj as? kotlinx.serialization.json.JsonObject
                        val msg = obj?.get("msg")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                        val errorDesc = obj?.get("error_description")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                        val message = obj?.get("message")?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                        msg ?: errorDesc ?: message ?: responseBody
                    } catch (_: Exception) {
                        responseBody
                    }

                    throw Exception(message)
                }
            }

            engine {
                config {
                    connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }
    }
}

