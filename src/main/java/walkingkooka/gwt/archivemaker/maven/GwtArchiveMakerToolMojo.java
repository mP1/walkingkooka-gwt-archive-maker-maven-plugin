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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * A plugin that packages a walkingkooka J2CL archive into a correct GWT archive.
 */
@Mojo(name = "makeGwtArchive", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GwtArchiveMakerToolMojo extends AbstractMojo {

    /**
     * The input jar file that needs to be transformed into a gwt jar.
     */
    @Parameter(
            required = true,
            property = "input"
    )
    private File input;

    /**
     * The output gwt jar file that will be created
     */
    @Parameter(
            required = true,
            property = "output"
    )
    private File output;

    /**
     * Path to the pom.xml file for the gwt archive.
     */
    @Parameter(
            required = true,
            property = "pom-file"
    )
    private File pomFile;

    @Override
    public void execute() throws MojoFailureException {
        try {
            GwtArchiveMakerTool.make(
                    this.input.toPath(),
                    this.output.toPath(),
                    this.pomFile.toPath()
            );
        } catch (final Exception cause) {
            throw new MojoFailureException("Gwt archive maker failed" + cause.getMessage(), cause);
        }
    }
}
