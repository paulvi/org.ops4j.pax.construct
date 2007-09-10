package org.ops4j.pax.construct.bundle;

/*
 * Copyright 2007 Stuart McCulloch
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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.PropertyUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @goal embed-jar
 * @aggregator true
 */
public class EmbedJarMojo extends AbstractMojo
{
    /**
     * @parameter expression="${groupId}"
     * @required
     */
    String groupId;

    /**
     * @parameter expression="${artifactId}"
     * @required
     */
    String artifactId;

    /**
     * @parameter expression="${version}"
     * @required
     */
    String version;

    /**
     * @parameter expression="${unpack}"
     */
    boolean unpack;

    /**
     * @parameter expression="${exportContents}"
     */
    String exportContents;

    /**
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    File targetDirectory;

    /**
     * @parameter expression="${overwrite}"
     */
    boolean overwrite;

    public void execute()
        throws MojoExecutionException
    {
        Pom pom = PomUtils.readPom( targetDirectory );
        if( !pom.isBundleProject() )
        {
            getLog().warn( "Cannot embed jar inside non-bundle project" );
            return;
        }

        // all compiled dependencies are automatically embedded
        Dependency dependency = new Dependency();
        dependency.setGroupId( groupId );
        dependency.setArtifactId( artifactId );
        dependency.setVersion( version );
        dependency.setScope( Artifact.SCOPE_COMPILE );

        // limit transitive nature
        dependency.setOptional( true );

        String id = groupId + ':' + artifactId + ':' + version;
        getLog().info( "Embedding " + id + " in " + pom.getId() );

        pom.addDependency( dependency, overwrite );
        pom.write();

        if( unpack || exportContents != null )
        {
            File bndConfig = new File( targetDirectory, "osgi.bnd" );

            if( !bndConfig.exists() )
            {
                try
                {
                    bndConfig.getParentFile().mkdirs();
                    bndConfig.createNewFile();
                }
                catch( Exception e )
                {
                    throw new MojoExecutionException( "Unable to create BND tool config file", e );
                }
            }

            Properties properties = PropertyUtils.loadProperties( bndConfig );
            boolean propertiesChanged = false;

            if( unpack )
            {
                // Use FELIX-308 to inline selected artifacts
                String embedDependency = properties.getProperty( "Embed-Dependency", "*;scope=compile|runtime" );
                String inlineClause = artifactId + ";groupId=" + groupId + ";inline=true";

                // check to see if its already inlined...
                if( embedDependency.indexOf( inlineClause ) < 0 )
                {
                    embedDependency += ',' + inlineClause;
                    properties.setProperty( "Embed-Dependency", embedDependency );
                    propertiesChanged = true;
                }
            }

            if( exportContents != null )
            {
                properties.setProperty( "-exportcontents", exportContents );
                propertiesChanged = true;
            }

            if( propertiesChanged )
            {
                OutputStream propertyStream = null;

                try
                {
                    propertyStream = new BufferedOutputStream( new FileOutputStream( bndConfig ) );
                    properties.store( propertyStream, null );
                }
                catch( Exception e )
                {
                    throw new MojoExecutionException( "Unable to save the new BND tool instructions", e );
                }
                finally
                {
                    IOUtil.close( propertyStream );
                }
            }
        }
    }
}
