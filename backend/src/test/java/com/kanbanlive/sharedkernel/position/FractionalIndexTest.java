package com.kanbanlive.sharedkernel.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Consume docs/contracts/fractional-index-vectors.json (compartido con el frontend). */
class FractionalIndexTest {

    static JsonNode vectors;

    @BeforeAll
    static void loadVectors() throws IOException {
        vectors = new ObjectMapper().readTree(new File("../docs/contracts/fractional-index-vectors.json"));
    }

    private static FractionalIndex nullable(JsonNode n) {
        return n.isNull() ? null : FractionalIndex.of(n.asText());
    }

    @Test
    void alphabetMatchesContract() {
        assertThat(FractionalIndex.ALPHABET).isEqualTo(vectors.get("alphabet").asText());
        assertThat(FractionalIndex.EXHAUSTION_LENGTH).isEqualTo(vectors.get("exhaustionLength").asInt());
    }

    @Test
    void betweenMatchesVectors() {
        for (JsonNode v : vectors.get("between")) {
            FractionalIndex prev = nullable(v.get("prev"));
            FractionalIndex next = nullable(v.get("next"));
            FractionalIndex result = FractionalIndex.between(prev, next);
            assertThat(result.value()).as("%s..%s", prev, next).isEqualTo(v.get("expected").asText());
        }
    }

    @Test
    void evenlySpacedMatchesVectors() {
        for (JsonNode v : vectors.get("evenlySpaced")) {
            List<String> expected = new ArrayList<>();
            v.get("expected").forEach(e -> expected.add(e.asText()));
            assertThat(FractionalIndex.evenlySpaced(v.get("count").asInt()).stream().map(FractionalIndex::value))
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void invalidValuesAreRejected() {
        for (JsonNode v : vectors.get("invalid")) {
            assertThatThrownBy(() -> FractionalIndex.of(v.asText())).isInstanceOf(ValidationException.class);
        }
        assertThatThrownBy(() -> FractionalIndex.of(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void orderingFollowsPlainStringOrder() {
        List<FractionalIndex> ordered = new ArrayList<>();
        vectors.get("orderedAscending").forEach(n -> ordered.add(FractionalIndex.of(n.asText())));
        for (int i = 0; i < ordered.size() - 1; i++) {
            assertThat(ordered.get(i)).isLessThan(ordered.get(i + 1));
            assertThat(ordered.get(i).value()).isLessThan(ordered.get(i + 1).value());
        }
    }

    @Test
    void betweenRejectsUnorderedBounds() {
        FractionalIndex a = FractionalIndex.of("V");
        assertThatThrownBy(() -> FractionalIndex.between(a, a)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> FractionalIndex.between(FractionalIndex.of("W"), a))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void detectsExhaustion() {
        assertThat(FractionalIndex.of("V").isExhausted()).isFalse();
        assertThat(FractionalIndex.of("V".repeat(FractionalIndex.EXHAUSTION_LENGTH)).isExhausted()).isTrue();
    }

    @Test
    void repeatedInsertionAtSamePointEventuallyExhaustsAndRebalanceRecovers() {
        FractionalIndex prev = FractionalIndex.of("V");
        FractionalIndex next = FractionalIndex.of("W");
        int inserts = 0;
        while (!next.isExhausted()) {
            next = FractionalIndex.between(prev, next);
            inserts++;
            assertThat(inserts).isLessThan(1000);
        }
        assertThat(FractionalIndex.evenlySpaced(50)).allSatisfy(i -> assertThat(i.isExhausted()).isFalse());
    }

    @Test
    void evenlySpacedIsStrictlyIncreasingAndLeavesRoom() {
        List<FractionalIndex> keys = FractionalIndex.evenlySpaced(500);
        assertThat(keys).hasSize(500).isSorted().doesNotHaveDuplicates();
        assertThat(FractionalIndex.evenlySpaced(0)).isEmpty();
        for (int i = 0; i < keys.size() - 1; i++) {
            FractionalIndex mid = FractionalIndex.between(keys.get(i), keys.get(i + 1));
            assertThat(mid.value().length()).isLessThanOrEqualTo(keys.get(i + 1).value().length() + 1);
        }
    }
}
