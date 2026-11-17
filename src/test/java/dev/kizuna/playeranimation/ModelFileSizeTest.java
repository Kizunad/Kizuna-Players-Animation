package dev.kizuna.playeranimation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 模型源文件不能大到推不上去：GitHub 拒收超过 100 MB 的文件，推送时才报，那时已经提交了。 */
class ModelFileSizeTest {
    private static final Path MODELS = Path.of("models");
    /** 到 90 MB 就失败，留出余地；和 scripts/bbmodel_store.py 的 MAX_BYTES 是同一个数。 */
    private static final long MAX_BYTES = 90_000_000L;

    @Test
    void everyBbmodelStaysWellUnderTheHostLimit() throws IOException {
        List<Path> models;
        try (Stream<Path> files = Files.walk(MODELS)) {
            models = files.filter(path -> path.toString().endsWith(".bbmodel")).toList();
        }
        assertFalse(models.isEmpty(), "models/ 下没找到 .bbmodel");
        for (Path model : models) {
            long size = Files.size(model);
            assertTrue(size <= MAX_BYTES, model + " 有 " + size / 1_000_000 + " MB，超过 90 MB："
                    + "把里面的动画再分一组（scripts/bbmodel_store.py 的 GROUPS）");
        }
    }
}
