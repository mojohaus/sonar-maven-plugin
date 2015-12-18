/*
 * The MIT License
 *
 * Copyright 2009 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.codehaus.mojo.sonar;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.MojoRule;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.fest.assertions.MapAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.MapAssert.entry;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarMojoTest
{
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public MojoRule mojoRule = new MojoRule();

    private Log mockedLogger;

    private SonarMojo getMojo( File baseDir )
        throws Exception
    {
        return (SonarMojo) mojoRule.lookupConfiguredMojo( baseDir, "sonar" );
    }

    @Before
    public void setUpMocks()
    {
        mockedLogger = mock( Log.class );
    }

    @Test
    public void executeMojo()
        throws Exception
    {
        File baseDir = executeProject( "sample-project" );

        // passed in the properties of the profile and project
        assertGlobalPropsContains( entry( "sonar.host.url1", "http://myserver:9000" ) );
        assertGlobalPropsContains( entry( "sonar.host.url2", "http://myserver:9000" ) );
    }

    @Test
    public void shouldExportBinaries()
        throws Exception
    {
        File baseDir = executeProject( "sample-project" );

        assertPropsContains( entry( "sonar.binaries", new File( baseDir, "target/classes" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportDefaultWarWebSource()
        throws Exception
    {
        File baseDir = executeProject( "sample-war-project" );
        assertPropsContains( entry( "sonar.sources",
                                    new File( baseDir, "src/main/webapp" ).getAbsolutePath() + ","
                                        + new File( baseDir, "pom.xml" ).getAbsolutePath() + ","
                                        + new File( baseDir, "src/main/java" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportOverridenWarWebSource()
        throws Exception
    {
        File baseDir = executeProject( "war-project-override-web-dir" );
        assertPropsContains( entry( "sonar.sources",
                                    new File( baseDir, "web" ).getAbsolutePath() + ","
                                        + new File( baseDir, "pom.xml" ).getAbsolutePath() + ","
                                        + new File( baseDir, "src/main/java" ).getAbsolutePath() ) );
    }

    @Test
    public void shouldExportDependencies()
        throws Exception
    {
        File localRepo = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/repository" );
        File baseDir = executeProject( "export-dependencies" );

        Properties outProps = readProps( "target/dump.properties" );
        String libJson = outProps.getProperty( "sonar.maven.projectDependencies" );

        JSONAssert.assertEquals( "[{\"k\":\"commons-io:commons-io\",\"v\":\"2.4\",\"s\":\"compile\",\"d\":["
            + "{\"k\":\"commons-lang:commons-lang\",\"v\":\"2.6\",\"s\":\"compile\",\"d\":[]}" + "]},"
            + "{\"k\":\"junit:junit\",\"v\":\"3.8.1\",\"s\":\"test\",\"d\":[]}]", libJson, true );

        assertThat( outProps.getProperty( "sonar.java.binaries" ) ).isEqualTo( new File( baseDir,
                                                                                         "target/classes" ).getAbsolutePath() );
        assertThat( outProps.getProperty( "sonar.java.test.binaries" ) ).isEqualTo( new File( baseDir,
                                                                                              "target/test-classes" ).getAbsolutePath() );
    }

    @Test
    public void shouldExportDependenciesWithSystemScopeTransitive()
        throws Exception
    {
        executeProject( "system-scope" );

        Properties outProps = readProps( "target/dump.properties" );
        String libJson = outProps.getProperty( "sonar.maven.projectDependencies" );

        JSONAssert.assertEquals( "[{\"k\":\"org.codehaus.xfire:xfire-core\",\"v\":\"1.2.6\",\"s\":\"compile\","
            + "\"d\":[{\"k\":\"javax.activation:activation\",\"v\":\"1.1.1\",\"s\":\"system\",\"d\":[]}]}]", libJson,
                                 true );
    }

    // MSONAR-113
    @Test
    public void shouldExportSurefireReportsPath()
        throws Exception
    {

        File baseDir = executeProject( "sample-project-with-surefire" );
        assertPropsContains( entry( "sonar.junit.reportsPath",
                                    new File( baseDir, "target/surefire-reports" ).getAbsolutePath() ) );
    }

    // MSONAR-113
    @Test
    public void shouldExportSurefireCustomReportsPath()
        throws Exception
    {
        File baseDir = executeProject( "sample-project-with-custom-surefire-path" );
        assertPropsContains( entry( "sonar.junit.reportsPath",
                                    new File( baseDir, "target/tests" ).getAbsolutePath() ) );
    }

    @Test
    public void findbugsExcludeFile()
        throws IOException, Exception
    {
        executeProject( "project-with-findbugs" );
        assertPropsContains( entry( "sonar.findbugs.excludeFilters", "findbugs-exclude.xml" ) );
        assertThat( readProps( "target/dump.properties.global" ) ).excludes( ( entry( "sonar.verbose", "true" ) ) );

    }

    @Test
    public void verbose()
        throws Exception
    {
        when( mockedLogger.isDebugEnabled() ).thenReturn( true );
        executeProject( "project-with-findbugs" );
        verify( mockedLogger, atLeastOnce() ).isDebugEnabled();
        assertThat( readProps( "target/dump.properties.global" ) ).includes( ( entry( "sonar.verbose", "true" ) ) );
    }

    private File executeProject( String projectName )
        throws Exception
    {
        File baseDir = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/" + projectName );
        SonarMojo mojo = getMojo( baseDir );
        // mojo.setLocalRepository( artifactRepo );
        mojo.setLog( mockedLogger );

        MavenSession session = (MavenSession) mojoRule.getVariableValueFromObject( mojo, "session" );
        RepositorySystem repoSystem = (RepositorySystem) mojoRule.lookup( RepositorySystem.class );
        RepositorySystemSession repoSystemSession = new DefaultRepositorySystemSession();
        ( (DefaultRepositorySystemSession) session.getRepositorySession() ).setLocalRepositoryManager( getLocalRepositoryManager( repoSystem,
                                                                                                                                  repoSystemSession ) );

        mojo.execute();

        return baseDir;
    }

    private LocalRepositoryManager getLocalRepositoryManager( RepositorySystem system,
                                                              RepositorySystemSession repoSystemSession )
    {
        return system.newLocalRepositoryManager( repoSystemSession, getLocalRepository() );
    }

    private LocalRepository getLocalRepository()
    {
        File localRepositoryPath = new File( "src/test/resources/org/codehaus/mojo/sonar/SonarMojoTest/repository" );
        return new LocalRepository( localRepositoryPath );
    }

    private void assertPropsContains( MapAssert.Entry... entries )
        throws FileNotFoundException, IOException
    {
        assertThat( readProps( "target/dump.properties" ) ).includes( entries );
    }

    private void assertGlobalPropsContains( MapAssert.Entry... entries )
        throws FileNotFoundException, IOException
    {
        assertThat( readProps( "target/dump.properties.global" ) ).includes( entries );
    }

    private Properties readProps( String filePath )
        throws FileNotFoundException, IOException
    {
        FileInputStream fis = null;
        try
        {
            File dump = new File( filePath );
            Properties props = new Properties();
            fis = new FileInputStream( dump );
            props.load( fis );
            return props;
        }
        finally
        {
            IOUtils.closeQuietly( fis );
        }
    }

}
