package com.ecren.billing.common;

import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID get() { return CURRENT.get(); }
    public static void set(UUID id) { CURRENT.set(id); }
    public static void clear() { CURRENT.remove(); }
}
