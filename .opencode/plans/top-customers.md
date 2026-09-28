# Plan: `topCustomers` in `GET /api/charts/totals`

## Locked decisions (user answered earlier)
- Revenue = **paid orders only**, net-of-tax per item line via `TaxBreakdown` (repo-wide TOTAL_REVENUE definition).
- Range = **all-time**, no params, **no plan gating** (matches `/totals` convention).
- Vehicles = **cars + bikes** (`totalVehicles` per customer).
- Size = **fixed 5**, ordered by `totalRevenue` desc.
- Home = existing `GET /api/charts/totals` → `ChartService.getTotals()`.

## Files & exact changes

1. **NEW `dto/TopCustomerResponse.java`**
```java
/** One of the org's top customers in GET /api/charts/totals — ranked by all-time paid, net-of-tax revenue across their service orders. totalVehicles = cars + bikes registered to them. */
public record TopCustomerResponse(String customerId, String name, long totalVehicles, BigDecimal totalRevenue) {}
```

2. **NEW `dto/TopCustomerRevenueRow.java`** (internal projection; customer-keyed revenue line)
```java
/** Query projection — one paid order-item line keyed by its customer, for ranking top customers. Internal. */
public record TopCustomerRevenueRow(UUID customerId, BigDecimal basePrice, BigDecimal taxPercentage, boolean taxIncluded, int quantity) {}
```

3. **`dto/OrgTotalsResponse.java`** — add 6th component `List<TopCustomerResponse> topCustomers` (additive; empty list when no paid orders).

4. **`repository/ServiceOrderRepository.java`** — add:
```java
@Query("""
        select new com.example.cleancarsapi.dto.TopCustomerRevenueRow(
            so.customerId, i.basePrice, i.taxPercentage, i.taxIncluded, i.quantity)
        from ServiceOrder so join ServiceOrderItem i on i.serviceOrderId = so.id
        where so.orgId = :orgId and so.paid = true
        """)
List<TopCustomerRevenueRow> findPaidCustomerRevenueLines(@Param("orgId") UUID orgId);
```
Imports: `TopCustomerRevenueRow`.

5. **`repository/CarRepository.java` / `BikeRepository.java`** — add derived finders (no JPQL needed):
```java
List<Car> findByOrgIdAndCustomerIdIn(UUID orgId, java.util.Collection<UUID> ids);
List<Bike> findByOrgIdAndCustomerIdIn(UUID orgId, java.util.Collection<UUID> ids);
```
(Vehicle counts computed in Java — top-5 customers' vehicles is a tiny set; matches repo's existing derived-finder habit and avoids an extra projection.)

6. **`service/ChartService.java`**
- `getTotals`: append `topCustomers(orgId)` as the 6th arg of `new OrgTotalsResponse(...)`.
- New private method:
```java
/** The org's top 5 customers by all-time paid, net-of-tax revenue (same TOTAL_REVENUE math). Omitted when there are no paid orders; customers deleted since their orders stay hidden (matches recentCustomers). */
private List<TopCustomerResponse> topCustomers(UUID orgId) {
    List<TopCustomerRevenueRow> lines = serviceOrders.findPaidCustomerRevenueLines(orgId);
    if (lines.isEmpty()) {
        return List.of();
    }
    Map<UUID, BigDecimal> revenue = new HashMap<>();
    for (TopCustomerRevenueRow line : lines) {
        revenue.merge(line.customerId(), netAmount(line.basePrice(), line.taxPercentage(), line.taxIncluded(), line.quantity()), BigDecimal::add);
    }
    Map<UUID, String> names = customers.findByOrgIdAndIdIn(orgId, revenue.keySet()).stream()
            .collect(Collectors.toMap(Customer::getId, Customer::getName));
    List<UUID> topIds = revenue.entrySet().stream()
            .filter(e -> names.containsKey(e.getKey()))
            .sorted(Comparator.comparing(Map.Entry<UUID, BigDecimal>::getValue, Comparator.reverseOrder())
                    .thenComparing(e -> names.getOrDefault(e.getKey(), "")))
            .limit(5)
            .map(Map.Entry::getKey)
            .toList();
    Map<UUID, Long> vehicles = Stream.concat(
                    cars.findByOrgIdAndCustomerIdIn(orgId, topIds).stream().map(Car::getCustomerId),
                    bikes.findByOrgIdAndCustomerIdIn(orgId, topIds).stream().map(Bike::getCustomerId))
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    return topIds.stream()
            .map(id -> new TopCustomerResponse(id.toString(), names.get(id),
                    vehicles.getOrDefault(id, 0L), revenue.get(id)))
            .toList();
}
```
- Extract shared net math:
```java
private static BigDecimal netAmount(ChartRevenueLine line) {
    return netAmount(line.basePrice(), line.taxPercentage(), line.taxIncluded(), line.quantity());
}
private static BigDecimal netAmount(BigDecimal basePrice, BigDecimal taxPercentage, boolean taxIncluded, int quantity) {
    return TaxBreakdown.of(basePrice, taxPercentage, taxIncluded).times(quantity).net();
}
```
- New imports: `TopCustomerResponse`, `TopCustomerRevenueRow`, `java.util.Comparator`, `java.util.function.Function`, `java.util.stream.Collectors`, `java.util.stream.Stream`, entity `Customer`; also `Car`/`Bike` only if lambda type hints need them (Stream.concat with method refs — not needed since map() infers… actually needs import if referencing Car::getCustomerId? No — method refs don't require imports. Skip unless compiler asks).
- Note: `netAmount(line.basePrice(), ...)` overload added; existing callers unchanged.

7. **`docs/FEATURE-CHARTS-DASHBOARD.md`** — extend `/api/charts/totals` bullet:
- add `topCustomers` to the returned shape sentence;
- describe: top 5 customers by all-time paid net-of-tax revenue (`TOTAL_REVENUE` definition); `totalVehicles` = cars + bikes registered to them; not gated by `stats_range_years`; empty list when no paid orders.

## Response shape (after change)
```json
{ "totalCars": 0, "totalBikes": 0, "totalServices": 0, "totalEmployees": 0, "totalCustomers": 0,
  "topCustomers": [{ "customerId": "...", "name": "...", "totalVehicles": 3, "totalRevenue": 5250.00 }] }
```

## Verify
- `./gradlew compileJava` (sandbox disabled — network needed)
- `./gradlew build` (boots Spring context vs Docker MySQL :3370 — ensure `docker compose up -d` first)
- Manual if desired: bootRun + GET /api/charts/totals with bearer; eyeball ordering.
- Grep verified: no test touches `OrgTotalsResponse` → nothing breaks.
