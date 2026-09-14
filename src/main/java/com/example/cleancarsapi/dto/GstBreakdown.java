package com.example.cleancarsapi.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Net / GST / gross derived from a stored base price plus GST inputs. Never
 * persisted — recomputed on every read, shared by {@link ServiceCatalogResponse}
 * and the service-order line items.
 *
 * <ul>
 *   <li>{@code included == true}  → {@code base} is the gross; net = base / (1 + rate).</li>
 *   <li>{@code included == false} → {@code base} is the net; gross = base * (1 + rate).</li>
 *   <li>{@code pct} null / zero → net == gross == base, no GST.</li>
 * </ul>
 * All amounts are scaled to 2 dp, {@code HALF_UP}.
 */
public record GstBreakdown(BigDecimal net, BigDecimal gst, BigDecimal gross) {

    public static GstBreakdown of(BigDecimal base, BigDecimal pct, boolean included) {
        BigDecimal b = base == null ? BigDecimal.ZERO : base;
        BigDecimal rate = pct == null ? BigDecimal.ZERO : pct;

        if (rate.signum() == 0) {
            BigDecimal scaled = scale(b);
            return new GstBreakdown(scaled, scale(BigDecimal.ZERO), scaled);
        }
        if (included) {
            BigDecimal gross = scale(b);
            BigDecimal net = b.divide(BigDecimal.ONE.add(rate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
            return new GstBreakdown(net, gross.subtract(net), gross);
        }
        BigDecimal net = scale(b);
        BigDecimal gst = scale(b.multiply(rate.movePointLeft(2)));
        return new GstBreakdown(net, gst, net.add(gst));
    }

    /** This per-unit breakdown multiplied by a whole-unit quantity. */
    public GstBreakdown times(int quantity) {
        BigDecimal q = BigDecimal.valueOf(quantity);
        return new GstBreakdown(scale(net.multiply(q)), scale(gst.multiply(q)), scale(gross.multiply(q)));
    }

    public GstBreakdown plus(GstBreakdown other) {
        return new GstBreakdown(net.add(other.net), gst.add(other.gst), gross.add(other.gross));
    }

    public static GstBreakdown zero() {
        BigDecimal z = scale(BigDecimal.ZERO);
        return new GstBreakdown(z, z, z);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
