package com.kanbanlive.testsupport;

import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fake de {@link BoardChangePublisher}: guarda los cambios publicados para aserciones en tests de aplicacion. */
public class RecordingBoardChanges implements BoardChangePublisher {

    public record Change(UUID boardId, String type, Object payload) {
    }

    public final List<Change> changes = new ArrayList<>();

    @Override
    public void publish(UUID boardId, String type, Object payload) {
        changes.add(new Change(boardId, type, payload));
    }

    public List<String> types() {
        return changes.stream().map(Change::type).toList();
    }
}
