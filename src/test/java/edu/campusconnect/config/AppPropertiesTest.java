package edu.campusconnect.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    private static AppProperties withDomains(List<String> domains) {
        return new AppProperties("http://x", "s".repeat(32), false, domains, List.of(), 30, 30, 15, 7, 15, 4,
                new AppProperties.Mail("log", "a@b.c"), new AppProperties.RateLimit(true));
    }

    @Test
    void domainsAreTrimmedAndCaseInsensitive() {
        AppProperties props = withDomains(List.of(" College.EDU ", "uni.ac.in", " "));

        assertEquals(List.of("college.edu", "uni.ac.in"), props.approvedDomains());
        assertTrue(props.isApprovedDomain("COLLEGE.edu"));
        assertTrue(props.isApprovedDomain("uni.ac.in"));
        assertFalse(props.isApprovedDomain("gmail.com"));
    }
}
