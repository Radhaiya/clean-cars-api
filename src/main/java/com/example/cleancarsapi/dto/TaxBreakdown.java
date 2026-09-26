package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Net / tax / gross derived from a stored base price plus tax inputs. Never
 * persisted — recomputed on every read, shared by {@link ServiceCatalogResponse}
 * and the service-order line items.
 *
 * <ul>
 *   <li>{@code included == true}  → {@code base} is the gross; net = base / (1 + rate).</li>
 *   <li>{@code included == false} → {@code base} is the net; gross = base * (1 + rate).</li>
 *   <li>{@code pct} null / zero → net == gross == base, no tax.</li>
 * </ul>
 * All amounts are scaled to 2 dp, {@code HALF_UP}.
 */
public record TaxBreakdown(BigDecimal net, BigDecimal tax, BigDecimal gross) {

    public static TaxBreakdown of(BigDecimal base, BigDecimal pct, boolean included) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        BigDecimal rate = pct == null ? BigDecimal.ZERO : pct;

        if (rate.signum() == 0) {
            BigDecimal scaled = scale(b);
            return new TaxBreakdown(scaled, scale(BigDecimal.ZERO), scaled);
        }
        if (included) {
            BigDecimal gross = scale(b);
            BigDecimal net = b.divide(BigDecimal.ONE.add(rate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
            return new TaxBreakdown(net, gross.subtract(net), gross);
        }
        BigDecimal net = scale(b);
        BigDecimal tax = scale(b.multiply(rate.movePointLeft(2)));
        return new TaxBreakdown(net, tax, net.add(tax));
    }

    /** This per-unit breakdown multiplied by a whole-unit quantity. */
    public TaxBreakdown times(int quantity) {
        BigDecimal q = BigDecimal.valueOf(quantity);
        return new TaxBreakdown(scale(net.multiply(q)), scale(tax.multiply(q)), scale(gross.multiply(q)));
    }

    public TaxBreakdown plus(TaxBreakdown other) {
        return new TaxBreakdown(net.add(other.net), tax.add(other.tax), gross.add(other.gross));
    }

    public static TaxBreakdown zero() {
        BigDecimal z = scale(BigDecimal.ZERO);
        return new TaxBreakdown(z, z, z);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
