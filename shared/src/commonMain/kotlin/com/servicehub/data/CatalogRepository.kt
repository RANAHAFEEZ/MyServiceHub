package com.servicehub.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

/** Read-only lists for the consumer side. Row Level Security decides what each user may see. */
class CatalogRepository(private val client: SupabaseClient = supabase) {

    suspend fun categories(): Result<List<CategoryDto>> = runCatching {
        client.from("categories").select().decodeList<CategoryDto>().sortedBy { it.sortOrder }
    }

    suspend fun offers(categoryId: String): Result<List<ProviderOfferDto>> = runCatching {
        client.from("provider_offers").select {
            filter { eq("category_id", categoryId) }
        }.decodeList<ProviderOfferDto>()
    }

    suspend fun providerDirectory(): Result<List<ProviderDirectoryDto>> = runCatching {
        client.from("provider_directory").select().decodeList<ProviderDirectoryDto>()
    }
}
