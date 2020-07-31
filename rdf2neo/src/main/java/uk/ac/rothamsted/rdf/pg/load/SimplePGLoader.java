package uk.ac.rothamsted.rdf.pg.load;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import uk.ac.rothamsted.rdf.pg.load.support.PGNodeHandler;
import uk.ac.rothamsted.rdf.pg.load.support.PGNodeLoadingProcessor;
import uk.ac.rothamsted.rdf.pg.load.support.PGRelationHandler;
import uk.ac.rothamsted.rdf.pg.load.support.PGRelationLoadingProcessor;
import uk.ac.rothamsted.rdf.pg.load.support.rdf.RdfDataManager;

@Component @Scope ( scopeName = "loadingSession" )
public abstract class SimplePGLoader
  <NH extends PGNodeHandler, RH extends PGRelationHandler, 
  NP extends PGNodeLoadingProcessor<NH>, RP extends PGRelationLoadingProcessor<RH>>
	implements PropertyGraphLoader, AutoCloseable
{	
	protected NP nodeLoader;
	protected RP relationLoader;

	protected RdfDataManager rdfDataManager = new RdfDataManager ();
	
	protected String name;
	
	protected Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * This does the basics. You might want to call it in your own extension, as a minimum to 
	 */
	@Override
	public void load ( String tdbPath, Object... opts ) 
	{
		loadBegin ( tdbPath, opts );
		loadBody ( tdbPath, opts );
		loadEnd ( tdbPath, opts );		
	}

	protected void loadBegin ( String tdbPath, Object... opts )
	{
		// Nothing in this version
	}

	protected void loadEnd ( String tdbPath, Object... opts )
	{
		log.info ( "{}RDF-PG conversion finished", getNamePrefix () );
	}
	
	protected void loadBody ( String tdbPath, Object... opts )
	{		
		try
		{
			RdfDataManager rdfMgr = this.getRdfDataManager ();

			rdfDataManager.open ( tdbPath );
			Dataset ds = rdfMgr.getDataSet ();
			
			final String namePrefx = this.getNamePrefix ();
			
			Txn.executeRead ( ds, () -> 
				log.info ( "{}Sending {} RDF triples to PG", namePrefx, ds.getUnionModel().size () )
			);
			
			// Nodes
			boolean doNodes = opts != null && opts.length > 0 ? (Boolean) opts [ 0 ] : true;
			if ( doNodes ) this.getPGNodeLoader ().process ( rdfMgr, opts );
	
			// Relations
			boolean doRels = opts != null && opts.length > 1 ? (Boolean) opts [ 1 ] : true;
			if ( doRels ) this.getPGRelationLoader ().process ( rdfMgr, opts );
			
		}
		catch ( Exception ex ) {
			throw new RuntimeException ( "Error while running the RDF/PG loader:" + ex.getMessage (), ex );
		}
	}
	
	/**
	 * Closes dependency objects. It DOES NOT deal with Neo4j driver closing, since this could be reused 
	 * across multiple instantiations of this class.
	 */
	@Override
	public void close ()
	{
		try
		{
			if ( this.getRdfDataManager () != null ) this.rdfDataManager.close ();
			if ( this.getPGNodeLoader () != null ) this.nodeLoader.close ();
			if ( this.getPGRelationLoader () != null ) this.relationLoader.close ();
		}
		catch ( Exception ex ) {
			throw new RuntimeException ( "Internal error while running the Cypher Loader: " + ex.getMessage (), ex );
		}
	}


	/**
	 * The manager to access to the underlining RDF source.
	 */
	public RdfDataManager getRdfDataManager ()
	{
		return rdfDataManager;
	}

	@Autowired
	public void setRdfDataManager ( RdfDataManager rdfDataManager )
	{
		this.rdfDataManager = rdfDataManager;
	}

	/**
	 * Works out the mapping and loading of nodes. 
	 *  
	 */
	public NP getPGNodeLoader ()
	{
		return nodeLoader;
	}

	@Autowired
	public void setPGNodeLoader ( NP nodeLoader )
	{
		this.nodeLoader = nodeLoader;
	}

	/**
	 * Works out the mapping and loading of relations.
	 */
	public RP getPGRelationLoader ()
	{
		return relationLoader;
	}

	@Autowired
	public void setPGRelationLoader ( RP relationLoader )
	{
		this.relationLoader = relationLoader;
	}
		
	/**
	 * Represents the nodes/relations kind that are loaded by this loader. This is prefixed to logging messages
	 * and is primarily useful when the simple loader is used by {@link MultiConfigPGLoader}. 
	 */
	public String getName ()
	{
		return name;
	}

	@Autowired ( required = false ) @Qualifier ( "defaultLoaderName" )
	public void setName ( String name )
	{
		this.name = name;
	}
	
	/**
	 * It's {@link #getName()}, possibly (if not empty/null) in a form like "[ name ] ", 
	 * which is used internally for logging and alike.
	 */
	protected String getNamePrefix ()
	{
		String result = StringUtils.trimToEmpty ( this.getName () );
		return result.isEmpty () ? "" : "[" + result + "] ";
	}
}