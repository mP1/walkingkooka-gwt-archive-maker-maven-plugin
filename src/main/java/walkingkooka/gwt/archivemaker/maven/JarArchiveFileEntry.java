/*
 * Copyright 2023 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.gwt.archivemaker.maven;

import walkingkooka.text.CharSequences;

import java.util.Arrays;

final class JarArchiveFileEntry {

    static JarArchiveFileEntry with(final String path,
                                    final long lastModified,
                                    final byte[] content) {
        return new JarArchiveFileEntry(
                path,
                lastModified,
                content.clone()
        );
    }

    private JarArchiveFileEntry(final String path,
                                final long lastModified,
                                final byte[] content) {
        this.path = path;
        this.lastModified = lastModified;
        this.content = content;
    }

    String path() {
        return this.path;
    }

    void setPath(final String path) {
        CharSequences.failIfNullOrEmpty(path, "path");

        if(path.startsWith("/")) {
            throw new IllegalArgumentException("Path must not begin with '/' but was " + CharSequences.quoteAndEscape(path));
        }

        this.moved = true;
        this.path = path;
        this.lastModified = System.currentTimeMillis();
    }

    private String path;

    boolean hasMoved() {
        return this.moved;
    }

    private boolean moved;

    long lastModified() {
        return this.lastModified;
    }

    private long lastModified;

    byte[] content() {
        final byte[] content = this.content;
        return null != content ? content.clone() : null;
    }

    void setContent(final byte[] content) {
        if (false == Arrays.equals(this.content, content)) {
            this.content = content;
            this.lastModified = System.currentTimeMillis();
        }
    }

    private byte[] content;

    // Object...........................................................................................................

    @Override
    public String toString() {
        return this.path;
    }
}
