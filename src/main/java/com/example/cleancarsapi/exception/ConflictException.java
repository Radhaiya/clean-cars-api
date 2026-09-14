package com.example.cleancarsapi.exception;

import lombok.Getter;

/** The request cannot be applied because it collides with existing data (HTTP 409). */
@Getter
public class ConflictException extends RuntimeException {

    /** Machine-readable code for the UI to branch on, e.g. {@code customer_phone_exists}. */
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ConflictException customerPhoneExists(String phone) {
        return new ConflictException("customer_phone_exists",
                "A customer with phone number " + phone + " already exists");
    }

    public static ConflictException carBrandNameExists(String name) {
        return new ConflictException("car_brand_name_exists",
                "A brand named '" + name + "' already exists");
    }

    public static ConflictException carModelNameExists(String name) {
        return new ConflictException("car_model_name_exists",
                "A model named '" + name + "' already exists for this brand");
    }

    public static ConflictException carBrandInUse() {
        return new ConflictException("car_brand_in_use",
                "This brand still has models and cannot be deleted");
    }

    public static ConflictException serviceCatalogNameExists(String name) {
        return new ConflictException("service_catalog_name_exists",
                "A service named '" + name + "' already exists");
    }

    public static ConflictException serviceCategoryNameExists(String name) {
        return new ConflictException("service_category_name_exists",
                "A category named '" + name + "' already exists");
    }

    public static ConflictException expenseCategoryNameExists(String name) {
        return new ConflictException("expense_category_name_exists",
                "A category named '" + name + "' already exists");
    }

    public static ConflictException trialAlreadyUsed() {
        return new ConflictException("trial_already_used",
                "This account has already used its free trial");
    }

    public static ConflictException userAlreadyHasOrg() {
        return new ConflictException("user_already_has_org",
                "This account already belongs to an organization (one account : one org)");
    }

    public static ConflictException orgAlreadySubscribed() {
        return new ConflictException("org_already_subscribed",
                "This organization already has an active subscription");
    }

    public static ConflictException statisticsNotAvailable() {
        return new ConflictException("statistics_not_available",
                "Your plan does not include the statistics page");
    }

    public static ConflictException statsRangeExceeded(int allowedYears) {
        return new ConflictException("stats_range_exceeded",
                "Your plan only includes the last " + allowedYears + " year(s) of statistics history");
    }
}
