package com.lms.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisplayNameTest {

    @Test
    @DisplayName("한글 이름은 성 + 이름, 띄어쓰기 없이 (Keycloak의 \"하나 김\" 대신 \"김하나\")")
    void koreanFamilyNameFirst() {
        assertThat(MembershipTenantResolver.displayName("하나 김", "하나", "김")).isEqualTo("김하나");
        assertThat(MembershipTenantResolver.displayName("하나 남궁", " 하나 ", "남궁")).isEqualTo("남궁하나");
    }

    @Test
    @DisplayName("라틴 문자 이름은 Keycloak이 만든 그대로, 한쪽만 있으면 name 클레임 그대로")
    void otherwiseKeepTheClaim() {
        assertThat(MembershipTenantResolver.displayName("Jane Doe", "Jane", "Doe")).isEqualTo("Jane Doe");
        assertThat(MembershipTenantResolver.displayName("하나", "하나", null)).isEqualTo("하나");
        assertThat(MembershipTenantResolver.displayName(null, null, null)).isNull();
    }
}
