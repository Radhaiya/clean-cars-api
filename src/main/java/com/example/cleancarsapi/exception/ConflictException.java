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

    public static ConflictException countryCodeRequired() {
        return new ConflictException("country_code_required",
                "Save your phone country code in Profile before adding a customer");
    }

    public static ConflictException vehicleOwnerDeleted() {
        return new ConflictException("vehicle_owner_deleted",
                "This vehicle's owner was deleted — transfer it to another customer before opening a new service order");
    }

    public static ConflictException ownerUnchanged() {
        return new ConflictException("owner_unchanged",
                "This customer already owns the vehicle");
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

    public static ConflictException amcNotInPlan() {
        return new ConflictException("amc_not_in_plan", "Your plan does not include AMC");
    }

    public static ConflictException amcPlanNameExists(String name) {
        return new ConflictException("amc_plan_name_exists", "An AMC plan named '" + name + "' already exists");
    }

    public static ConflictException amcPlanArchived() {
        return new ConflictException("amc_plan_archived", "This AMC plan is archived — restore it before adding variants");
    }

    public static ConflictException amcSlotUsed() {
        return new ConflictException("amc_slot_used", "This AMC has already been used for the current period");
    }

    public static ConflictException amcExpired() {
        return new ConflictException("amc_expired", "This AMC has expired");
    }

    public static ConflictException amcNotStarted() {
        return new ConflictException("amc_not_started", "This AMC has not started yet");
    }

    public static ConflictException amcOrderLocked() {
        return new ConflictException("amc_order_locked",
                "An AMC order's services and payments are fixed — create a normal service order for anything else");
    }

    public static ConflictException amcOrderReopen() {
        return new ConflictException("amc_order_reopen",
                "A cancelled AMC order cannot be reopened — create a new one to use the AMC again");
    }

    public static ConflictException amcPlanSold() {
        return new ConflictException("amc_plan_sold", "This AMC plan has been sold, so it can only be archived, not deleted");
    }

    public static ConflictException amcVariantSold() {
        return new ConflictException("amc_variant_sold", "This variant has been sold, so it can only be archived, not deleted");
    }

    public static ConflictException amcVariantArchived() {
        return new ConflictException("amc_variant_archived", "This AMC plan or variant is archived and cannot be sold");
    }

    public static ConflictException amcVariantExists() {
        return new ConflictException("amc_variant_exists",
                "This plan already has a variant with the same tenure and frequency");
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

    public static ConflictException trialNotUsed() {
        return new ConflictException("trial_not_used",
                "Start your free trial instead — it has not been used yet");
    }

    public static ConflictException userAlreadyHasOrg() {
        return new ConflictException("user_already_has_org",
                "This account already belongs to an organization (one account : one org)");
    }

    public static ConflictException orgAlreadySubscribed() {
        return new ConflictException("org_already_subscribed",
                "This organization already has an active subscription");
    }

    public static ConflictException phoneVerificationRequired() {
        return new ConflictException("phone_verification_required",
                "Verify your phone number (POST /api/me/phone/start + /check) before buying a plan");
    }

    public static ConflictException userAlreadyInOrg(String email) {
        return new ConflictException("user_already_in_org",
                email + " already belongs to an organization — a member cannot be invited");
    }

    public static ConflictException userHasOwnPlan(String email) {
        return new ConflictException("user_has_own_plan",
                email + " has a live paid plan on their own organization — they cannot be invited");
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

    public static ConflictException paymentExceedsRemaining(java.math.BigDecimal remaining) {
        return new ConflictException("payment_exceeds_remaining",
                "This payment is more than the " + remaining.toPlainString() + " still remaining on the order");
    }

    public static ConflictException oneTimeAlreadyPaid() {
        return new ConflictException("one_time_already_paid",
                "A one-time order takes a single payment, and this one already has it — switch the order to split to add more");
    }

    public static ConflictException orderTotalZero() {
        return new ConflictException("order_total_zero",
                "The order total is zero — add service lines before recording a payment");
    }

    public static ConflictException paymentPlanHasPayments() {
        return new ConflictException("payment_plan_has_payments",
                "An order with payments on it cannot go back to one-time — remove its payments first");
    }

    public static ConflictException planRazorpayIdExists(String razorpayPlanId) {
        return new ConflictException("plan_razorpay_id_exists",
                "Razorpay plan id " + razorpayPlanId + " is already used by another plan");
    }

    public static ConflictException planHasSubscriptions() {
        return new ConflictException("plan_has_subscriptions",
                "This plan has subscriptions, so it can only be hidden (is_public = false), not deleted");
    }

    public static ConflictException trialPlanProtected() {
        return new ConflictException("trial_plan_protected", "The Trial plan cannot be deleted");
    }
}
