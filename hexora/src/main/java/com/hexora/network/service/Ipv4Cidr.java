package com.hexora.network.service;

import java.util.regex.Pattern;

record Ipv4Cidr(long network, int prefixLength) {

    private static final Pattern OCTET = Pattern.compile("0|[1-9][0-9]{0,2}");
    private static final long IPV4_MASK = 0xffff_ffffL;

    static Ipv4Cidr parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("An IPv4 CIDR is required");
        }
        String[] parts = value.trim().split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR must be an IPv4 network and prefix");
        }

        long address = parseAddress(parts[0]);
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CIDR prefix must be between 1 and 32", exception);
        }
        if (prefix < 1 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be between 1 and 32");
        }

        long mask = mask(prefix);
        long network = address & mask;
        if (network != address) {
            throw new IllegalArgumentException("CIDR must use the canonical network address");
        }
        return new Ipv4Cidr(network, prefix);
    }

    static long parseAddress(String value) {
        if (value == null) {
            throw new IllegalArgumentException("An IPv4 literal is required");
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Only literal IPv4 addresses are allowed");
        }
        long result = 0;
        for (String octet : octets) {
            if (!OCTET.matcher(octet).matches()) {
                throw new IllegalArgumentException("Only literal IPv4 addresses are allowed");
            }
            int valuePart = Integer.parseInt(octet);
            if (valuePart > 255) {
                throw new IllegalArgumentException("IPv4 octets must be between 0 and 255");
            }
            result = (result << 8) | valuePart;
        }
        return result;
    }

    boolean containsAddress(String value) {
        return containsAddress(parseAddress(value));
    }

    boolean containsAddress(long address) {
        return (address & mask(prefixLength)) == network;
    }

    boolean contains(Ipv4Cidr other) {
        return prefixLength <= other.prefixLength && containsAddress(other.network);
    }

    boolean isPrivateOrLocal() {
        long first = network >>> 24;
        long second = (network >>> 16) & 0xff;
        return first == 10
            || first == 172 && second >= 16 && second <= 31
            || first == 192 && second == 168
            || first == 127
            || first == 169 && second == 254;
    }

    String canonicalValue() {
        return addressText(network) + "/" + prefixLength;
    }

    static String addressText(long address) {
        return ((address >>> 24) & 0xff) + "."
            + ((address >>> 16) & 0xff) + "."
            + ((address >>> 8) & 0xff) + "."
            + (address & 0xff);
    }

    private static long mask(int prefix) {
        return (IPV4_MASK << (32 - prefix)) & IPV4_MASK;
    }
}
