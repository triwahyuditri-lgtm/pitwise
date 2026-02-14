package com.example.pitwise.data.remote

/**
 * Supabase configuration constants.
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://dqijasvfmgeuddsfvyqk.supabase.co"

    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRxaWphc3ZmbWdldWRkc2Z2eXFrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzA4MzU5NTMsImV4cCI6MjA4NjQxMTk1M30.7aPgscsXlCLqURHUs1EL5mKfE1zMVHJh4zXsgEQGi3o"

    // REST API endpoints
    const val AUTH_ENDPOINT = "$SUPABASE_URL/auth/v1"
    const val REST_ENDPOINT = "$SUPABASE_URL/rest/v1"
}
