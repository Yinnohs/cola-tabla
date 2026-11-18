package com.kanbanlive.sharedkernel.position;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class FractionalIndexPropertiesTest {

    @Provide
    Arbitrary<FractionalIndex> indexes() {
        Arbitrary<String> body = Arbitraries.strings().withChars(FractionalIndex.ALPHABET.toCharArray())
                .ofMinLength(0).ofMaxLength(12);
        Arbitrary<Character> last = Arbitraries.of(FractionalIndex.ALPHABET.substring(1).chars()
                .mapToObj(c -> (char) c).toList());
        return Combinators.combine(body, last).as((b, l) -> FractionalIndex.of(b + l));
    }

    @Provide
    Arbitrary<FractionalIndex> nullableIndexes() {
        return indexes().injectNull(0.2);
    }

    @Provide
    Arbitrary<List<Integer>> positions() {
        return Arbitraries.integers().between(0, 10_000).list().ofMinSize(1).ofMaxSize(300);
    }

    @Property
    void orderIsTotalAndMatchesStringOrder(@ForAll("indexes") FractionalIndex a, @ForAll("indexes") FractionalIndex b,
            @ForAll("indexes") FractionalIndex c) {
        assertThat(Integer.signum(a.compareTo(b))).isEqualTo(Integer.signum(a.value().compareTo(b.value())));
        assertThat(Integer.signum(a.compareTo(b))).isEqualTo(-Integer.signum(b.compareTo(a)));
        if (a.compareTo(b) <= 0 && b.compareTo(c) <= 0) {
            assertThat(a.compareTo(c)).isLessThanOrEqualTo(0);
        }
        assertThat(a.compareTo(b) == 0).isEqualTo(a.equals(b));
    }

    @Property
    void betweenIsStrictlyBetween(@ForAll("nullableIndexes") FractionalIndex x,
            @ForAll("nullableIndexes") FractionalIndex y) {
        FractionalIndex prev = x;
        FractionalIndex next = y;
        if (x != null && y != null) {
            int cmp = x.compareTo(y);
            Assume.that(cmp != 0);
            prev = cmp < 0 ? x : y;
            next = cmp < 0 ? y : x;
        }
        FractionalIndex mid = FractionalIndex.between(prev, next);
        if (prev != null) {
            assertThat(mid).isGreaterThan(prev);
        }
        if (next != null) {
            assertThat(mid).isLessThan(next);
        }
    }

    @Property
    void randomInsertionsNeverCollideAndStayOrdered(@ForAll("positions") List<Integer> positions) {
        List<FractionalIndex> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int p : positions) {
            int at = Math.floorMod(p, list.size() + 1);
            FractionalIndex prev = at == 0 ? null : list.get(at - 1);
            FractionalIndex next = at == list.size() ? null : list.get(at);
            FractionalIndex created = FractionalIndex.between(prev, next);
            assertThat(seen.add(created.value())).as("collision on %s", created).isTrue();
            list.add(at, created);
        }
        assertThat(list).isSorted();
    }

    @Property
    void repeatedInsertionsAtSameSpotNeverCollide(@ForAll boolean atEnd, @ForAll("indexes") FractionalIndex anchor) {
        Set<String> seen = new HashSet<>();
        FractionalIndex limit = anchor;
        for (int i = 0; i < 100; i++) {
            FractionalIndex created = atEnd ? FractionalIndex.between(limit, null) : FractionalIndex.between(null, limit);
            assertThat(seen.add(created.value())).isTrue();
            limit = created;
        }
    }

    @Property
    void evenlySpacedIsStrictlyIncreasing(@ForAll @IntRange(min = 0, max = 3000) int n) {
        List<FractionalIndex> keys = FractionalIndex.evenlySpaced(n);
        assertThat(keys).hasSize(n).isSorted().doesNotHaveDuplicates();
    }
}
