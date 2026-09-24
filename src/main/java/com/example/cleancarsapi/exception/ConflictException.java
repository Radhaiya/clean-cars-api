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

    public static ConflictException bikeBrandNameExists(String name) {
        return new ConflictException("bike_brand_name_exists",
                "A brand named '" + name + "' already exists");
    }

    public static ConflictException bikeModelNameExists(String name) {
        return new ConflictException("bike_model_name_exists",
                "A model named '" + name + "' already exists for this brand");
    }

    public static ConflictException bikeBrandInUse() {
        return new ConflictException("bike_brand_in_use",
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

    public static ConflictException userAlreadyInOrg(String email) {
        return new ConflictException("user_already_in_org",
                email + " already belongs to an organization — a member cannot be invited");
    }

    public static ConflictException inviteAlreadyPending(String email) {
        return new ConflictException("invite_already_pending",
                "A pending invite for " + email + " already exists for this organization");
    }

    public static ConflictException userLimitReached(int maxUsers) {
        return new ConflictException("user_limit_reached",
                "Your plan allows at most " + maxUsers + " member(s) — upgrade to add more");
    }

    public static ConflictException employeeAlreadyLinked() {
        return new ConflictException("employee_already_linked",
                "This employee already has a linked account — remove the link before inviting again");
    }

    public static ConflictException employeeEmailExists(String email) {
        return new ConflictException("employee_email_exists",
                "An employee with email " + email + " already exists in this organization");
    }

    public static ConflictException inviteNotPending() {
        return new ConflictException("invite_not_pending",
                "Only a pending invite can be acted on");
    }

    public static ConflictException inviteExpired() {
        return new ConflictException("invite_expired",
                "This invite has expired — ask the owner to send a new one");
    }

    public static ConflictException inviteEmailMismatch() {
        return new ConflictException("invite_email_mismatch",
                "Sign in with the account this invite was sent to");
    }

    public static ConflictException inviteNoEmail() {
        return new ConflictException("invite_no_email",
                "Invites are addressed by email — this account has no email on file");
    }

    public static ConflictException orgNoLiveSubscription() {
        return new ConflictException("org_no_live_subscription",
                "The organization has no live plan or trial — renew it before inviting members");
    }

    public static ConflictException ownerCannotLeave() {
        return new ConflictException("owner_cannot_leave",
                "The org owner cannot leave — the owner role has a dedicated exit (delete the org)");
    }

    public static ConflictException userNotInOrg() {
        return new ConflictException("user_not_in_org",
                "This account does not belong to an organization");
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
