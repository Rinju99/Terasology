/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.persistence.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Helper class for methods around {@link SaveTransaction}s that are also needed outside of the save transaction.
 *
 * @author Florian <florian@fkoeberle.de>
 */
public class SaveTransactionHelper {
    private static final Logger logger = LoggerFactory.getLogger(SaveTransactionHelper.class);
    private final StoragePathProvider storagePathProvider;

    public SaveTransactionHelper(StoragePathProvider storagePathProvider) {
        this.storagePathProvider = storagePathProvider;
    }

    public void cleanupSaveTransactionDirectory() throws IOException {
        Path directory = storagePathProvider.getUnfinishedSaveTransactionPath();
        if (!Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Merges all outstanding changes into the save game. If this operation gets interrupted it can be started again
     * without any file corruption when the file system supports atomic moves.
     */
    public void mergeChanges() throws IOException {
        final Path sourceDirectory = storagePathProvider.getUnmergedChangesPath();
        final Path targetDirectory = storagePathProvider.getStoragePathDirectory();

        Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() {
            boolean atomicNotPossibleLogged = false;

            @Override
            public FileVisitResult preVisitDirectory(Path sourceSubDir, BasicFileAttributes attrs) throws IOException {
                Path targetSubDir = targetDirectory.resolve(sourceDirectory.relativize(sourceSubDir));
                if (!Files.isDirectory(targetSubDir)) {
                    Files.createDirectory(targetSubDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path sourcePath, BasicFileAttributes attrs) throws IOException {
                Path targetPath = targetDirectory.resolve(sourceDirectory.relativize(sourcePath));
                try {
                    // Delete file, as behavior of atomic move is undefined if target file exists:
                    Files.deleteIfExists(targetPath);
                    Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    if (!atomicNotPossibleLogged) {
                        logger.warn("Atomic move was not possible, doing it non atomically...");
                        atomicNotPossibleLogged = true;
                    }
                    Files.move(sourcePath, targetPath);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
