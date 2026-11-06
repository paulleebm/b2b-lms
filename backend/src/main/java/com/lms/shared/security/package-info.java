/**
 * Exposed to every module (shared kernel). Without this, Spring Modulith treats sub-packages of a
 * module as internal and fails ModularityTest when another module uses them.
 */
@org.springframework.modulith.NamedInterface("security")
package com.lms.shared.security;
