package com.lms.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7AndIetfVariant() {
        UUID id = UuidV7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void idsCreatedLaterSortLater() throws InterruptedException {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(UuidV7.generate());
            Thread.sleep(2);
        }
        List<String> asText = ids.stream().map(UUID::toString).toList();
        assertThat(asText).isSorted();
    }
}
