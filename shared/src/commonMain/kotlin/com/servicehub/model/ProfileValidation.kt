package com.servicehub.model

import com.servicehub.model.ProviderProfile

/** Returns a list of problems; empty list means the profile can be submitted. */
fun validateProviderProfile(p: ProviderProfile): List<String> {
    val errors = mutableListOf<String>()
    if (p.name.trim().length < 2) errors += "Name is too short"
    val digits = p.phone.filter { it.isDigit() }
    if (digits.length !in 8..15) errors += "Phone number must have 8 to 15 digits"
    if (p.experienceYears !in 0..60) errors += "Experience must be between 0 and 60 years"
    if (p.categories.isEmpty()) errors += "Choose at least one category"
    if (p.rate.minor <= 0) errors += "Rate must be greater than zero"
    if (p.city.isBlank()) errors += "City is required"
    return errors
}
