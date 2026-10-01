package com.example.cleancarsapi.dto;

/** Archive (true) or restore (false) an AMC plan or variant. Archived ones cannot be sold. */
public record AmcArchiveRequest(boolean archived) {
}
