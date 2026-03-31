package com.career.assistant.infrastructure.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocalVectorStoreTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── 정상 경로 ──

    @Test
    void put_정상저장() {
        LocalVectorStore store = createStore("put");
        store.put(1L, new float[]{1.0f, 0.0f});

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.ids()).containsExactly(1L);
    }

    @Test
    void remove_정상삭제() {
        LocalVectorStore store = createStore("remove");
        store.put(1L, new float[]{1.0f});
        store.remove(1L);

        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    void putAll_배치삽입() {
        LocalVectorStore store = createStore("putAll");
        store.putAll(Map.of(1L, new float[]{1.0f}, 2L, new float[]{2.0f}));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.ids()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void removeAll_배치삭제() {
        LocalVectorStore store = createStore("removeAll");
        store.putAll(Map.of(1L, new float[]{1.0f}, 2L, new float[]{2.0f}, 3L, new float[]{3.0f}));
        store.removeAll(Set.of(1L, 2L));

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.ids()).containsExactly(3L);
    }

    @Test
    void clearAndSave_전체삭제() {
        LocalVectorStore store = createStore("clear");
        store.putAll(Map.of(1L, new float[]{1.0f}, 2L, new float[]{2.0f}));
        store.clearAndSave();

        assertThat(store.isEmpty()).isTrue();
    }

    @Test
    void 파일에서_복원() {
        LocalVectorStore store1 = createStore("reload");
        store1.putAll(Map.of(1L, new float[]{1.0f}, 2L, new float[]{2.0f}));

        LocalVectorStore store2 = createStore("reload");
        store2.init(); // @PostConstruct 대체 — 파일에서 load

        assertThat(store2.size()).isEqualTo(2);
        assertThat(store2.ids()).containsExactlyInAnyOrder(1L, 2L);
    }

    // ── 영속화 실패 시 롤백 ──

    @Test
    void put_영속화실패시_롤백() throws IOException {
        String name = "rollback-put";
        LocalVectorStore store = createStore(name);
        store.put(1L, new float[]{1.0f});

        breakFilesystem(name);
        store.put(2L, new float[]{2.0f}); // persist 실패 → 2L 롤백

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.ids()).containsExactly(1L);
    }

    @Test
    void remove_영속화실패시_롤백() throws IOException {
        String name = "rollback-remove";
        LocalVectorStore store = createStore(name);
        store.put(1L, new float[]{1.0f});

        breakFilesystem(name);
        store.remove(1L); // persist 실패 → 1L 복원

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.ids()).containsExactly(1L);
    }

    @Test
    void putAll_영속화실패시_롤백() throws IOException {
        String name = "rollback-putAll";
        LocalVectorStore store = createStore(name);
        store.put(1L, new float[]{1.0f});

        breakFilesystem(name);
        store.putAll(Map.of(2L, new float[]{2.0f}, 3L, new float[]{3.0f})); // persist 실패 → 2L, 3L 롤백

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.ids()).containsExactly(1L);
    }

    @Test
    void removeAll_영속화실패시_롤백() throws IOException {
        String name = "rollback-removeAll";
        LocalVectorStore store = createStore(name);
        store.putAll(Map.of(1L, new float[]{1.0f}, 2L, new float[]{2.0f}));

        breakFilesystem(name);
        store.removeAll(Set.of(1L, 2L)); // persist 실패 → 1L, 2L 복원

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.ids()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void clearAndSave_영속화실패시_롤백() throws IOException {
        String name = "rollback-clear";
        LocalVectorStore store = createStore(name);
        store.putAll(Map.of(1L, new float[]{1.0f}, 2L, new float[]{2.0f}));

        breakFilesystem(name);
        store.clearAndSave(); // persist 실패 → 전체 복원

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.ids()).containsExactlyInAnyOrder(1L, 2L);
    }

    // ── 헬퍼 ──

    private LocalVectorStore createStore(String name) {
        Path storePath = tempDir.resolve(name).resolve("vectors.json");
        return new LocalVectorStore(objectMapper, storePath.toString());
    }

    /**
     * storePath의 부모 디렉토리를 파일로 교체하여 tryPersist() 실패를 유도한다.
     * Files.createDirectories()가 FileAlreadyExistsException을 던지게 된다.
     */
    private void breakFilesystem(String name) throws IOException {
        Path dir = tempDir.resolve(name);
        // 디렉토리 내 파일 삭제
        try (var stream = Files.list(dir)) {
            stream.forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        }
        // 디렉토리 삭제 후 같은 이름으로 파일 생성
        Files.delete(dir);
        Files.writeString(dir, "blocker");
    }
}
