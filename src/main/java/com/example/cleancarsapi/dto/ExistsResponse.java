package com.example.cleancarsapi.dto;

/** Answer to a "does this phone / vehicle number already exist?" pre-check (the save is still checked server-side). */
public record ExistsResponse(boolean exists) {
}
