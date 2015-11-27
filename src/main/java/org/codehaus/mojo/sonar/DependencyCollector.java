package org.codehaus.mojo.sonar;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.filter.AncestorOrSelfDependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.filter.DependencyNodeFilter;
import org.apache.maven.shared.dependency.graph.traversal.BuildingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.CollectingDependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;
import org.apache.maven.shared.dependency.graph.traversal.FilteringDependencyNodeVisitor;

public class DependencyCollector
{

    private final DependencyGraphBuilder dependencyGraphBuilder;

    private final MavenSession session;

    public DependencyCollector( DependencyGraphBuilder dependencyGraphBuilder, MavenSession session )
    {
        this.dependencyGraphBuilder = dependencyGraphBuilder;
        this.session = session;
    }

    private static class Dependency
    {

        private final String key;

        private final String version;

        private String scope;

        List<Dependency> dependencies = new ArrayList<Dependency>();

        public Dependency( String key, String version )
        {
            this.key = key;
            this.version = version;
        }

        public String key()
        {
            return key;
        }

        public String version()
        {
            return version;
        }

        public String scope()
        {
            return scope;
        }

        public Dependency setScope( String scope )
        {
            this.scope = scope;
            return this;
        }

        public List<Dependency> dependencies()
        {
            return dependencies;
        }
    }

    private List<Dependency> collectProjectDependencies( MavenProject project )
    {
        final List<Dependency> result = new ArrayList<Dependency>();
        try
        {
            ArtifactFilter filter = null;

            ProjectBuildingRequest projectBuildingRequest = session.getProjectBuildingRequest();
            projectBuildingRequest.setProject( project );
            DependencyNode root =
                dependencyGraphBuilder.buildDependencyGraph( projectBuildingRequest, filter );

            DependencyNodeVisitor visitor = new BuildingDependencyNodeVisitor( new DependencyNodeVisitor()
            {

                private Deque<Dependency> stack = new ArrayDeque<Dependency>();

                @Override
                public boolean visit( DependencyNode node )
                {
                    if ( node.getParent() != null && node.getParent() != node )
                    {
                        Dependency dependency = toDependency( node );
                        if ( stack.isEmpty() )
                        {
                            result.add( dependency );
                        }
                        else
                        {
                            stack.peek().dependencies().add( dependency );
                        }
                        stack.push( dependency );
                    }
                    return true;
                }

                @Override
                public boolean endVisit( DependencyNode node )
                {
                    if ( !stack.isEmpty() )
                    {
                        stack.pop();
                    }
                    return true;
                }
            } );

            CollectingDependencyNodeVisitor collectingVisitor = new CollectingDependencyNodeVisitor();
            root.accept( collectingVisitor );

            DependencyNodeFilter secondPassFilter =
                new AncestorOrSelfDependencyNodeFilter( collectingVisitor.getNodes() );
            visitor = new FilteringDependencyNodeVisitor( visitor, secondPassFilter );

            root.accept( visitor );

        }
        catch ( DependencyGraphBuilderException e )
        {
            throw new IllegalStateException( "Can not load the graph of dependencies of the project " + project, e );
        }
        return result;
    }

    private Dependency toDependency( DependencyNode node )
    {
        String key = String.format( "%s:%s", node.getArtifact().getGroupId(), node.getArtifact().getArtifactId() );
        String version = node.getArtifact().getBaseVersion();
        return new Dependency( key, version ).setScope( node.getArtifact().getScope() );
    }

    public String toJson( MavenProject project )
    {
        return dependenciesToJson( collectProjectDependencies( project ) );
    }

    private String dependenciesToJson( List<Dependency> deps )
    {
        StringBuilder json = new StringBuilder();
        json.append( '[' );
        serializeDeps( json, deps );
        json.append( ']' );
        return json.toString();
    }

    private void serializeDeps( StringBuilder json, List<Dependency> deps )
    {
        for ( Iterator<Dependency> dependencyIt = deps.iterator(); dependencyIt.hasNext(); )
        {
            serializeDep( json, dependencyIt.next() );
            if ( dependencyIt.hasNext() )
            {
                json.append( ',' );
            }
        }
    }

    private void serializeDep( StringBuilder json, Dependency dependency )
    {
        json.append( "{" );
        json.append( "\"k\":\"" );
        json.append( dependency.key() );
        json.append( "\",\"v\":\"" );
        json.append( dependency.version() );
        json.append( "\",\"s\":\"" );
        json.append( dependency.scope() );
        json.append( "\",\"d\":[" );
        serializeDeps( json, dependency.dependencies() );
        json.append( "]" );
        json.append( "}" );
    }
}
