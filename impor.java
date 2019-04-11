package neo4jtest;

import java.io.File;
import java.util.Map;

import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.UniqueFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.HermiT.Reasoner;

public class impor {
	 private static void registerShutdownHook(final GraphDatabaseService graphDB) {
	        Runtime.getRuntime().addShutdownHook(
	                new Thread() {
	                    public void run() {
	                        //Shutdown the Database
	                        System.out.println("Server is shutting down");
	                        graphDB.shutdown();
	                    }
	                }
	        );
	    }
	
	 //The function gets node from string if exists or creates it if not exists
    public static Node getOrCreateNodeWithUniqueFactory( String url, GraphDatabaseService graphDb )
	 {
    	 UniqueFactory<Node> factory = new UniqueFactory.UniqueNodeFactory( graphDb, "url" )
	     {
	         @Override
	         protected void initialize(Node created, Map<String, Object> properties )
	         {
	             created.setProperty( "url", properties.get( "url" ) );
	         }

	
	     };

	     return factory.getOrCreate( "url", url );
	 }

	
	private static void importOntology(OWLOntology ontology,GraphDatabaseService db) throws Exception {
		//OWLReasoner reasoner = OWLReasonerFactory.createNonBufferingReasoner(ontology);
	     OWLReasoner reasoner = new Reasoner(ontology);
	          if (!reasoner.isConsistent()) {
	        	  System.out.println("Ontology is inconsistent");
	             //  logger.error("Ontology is inconsistent");
	               //throw your exception of choice here
	               throw new Exception("Ontology is inconsistent");
	          }
	          Transaction tx = db.beginTx();	          
	          try {
	           Node thingNode = getOrCreateNodeWithUniqueFactory("owl:Thing",db);
	           
	          for (OWLClass c :ontology.getClassesInSignature(true)) {
	               String classString = c.toString();
	               
	               //print class
	               //System.out.println(classString);
	               
	               if (classString.contains("#")) {
	                    classString = classString.substring(
	                    classString.indexOf("#")+1,classString.lastIndexOf(">"));
	               }
	                    Node classNode = getOrCreateNodeWithUniqueFactory(classString,db);

	                    NodeSet<OWLClass> superclasses = reasoner.getSuperClasses(c, true);
	                    if (superclasses.isEmpty()) {
	                    	 //System.out.println("empty");
	                         classNode.createRelationshipTo(thingNode, DynamicRelationshipType.withName("isA"));
	                         } else {
	                         for (org.semanticweb.owlapi.reasoner.Node<OWLClass>
	                         parentOWLNode: superclasses) {
	                        	 //System.out.println("not empty");
	                              OWLClassExpression parent =
	                              parentOWLNode.getRepresentativeElement();
	                              String parentString = parent.toString();
	                              
	                              //System.out.println(""+parentString);
	                              
	                              if (parentString.contains("#")) {
	                                   parentString = parentString.substring(
	                                   parentString.indexOf("#")+1,
	                                   parentString.lastIndexOf(">"));
	                              }
	                              Node parentNode =
	                              getOrCreateNodeWithUniqueFactory(parentString,db);
	                              classNode.createRelationshipTo(parentNode,
	                                  DynamicRelationshipType.withName("isA"));
	                         }
	                    }



	                    for (org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual> in
	                         : reasoner.getInstances(c, true)) {
	                         OWLNamedIndividual i = in.getRepresentativeElement();
	                         
	                         String indString = i.toString();
	                         
	                         //print individuals
	                         System.out.println(i);
	                         
	                         if (indString.contains("#")) {
	                              indString = indString.substring(
	                                   indString.indexOf("#")+1,indString.lastIndexOf(">"));
	                         }
	                         Node individualNode =getOrCreateNodeWithUniqueFactory(indString,db);
	                         individualNode.createRelationshipTo(classNode,
	                         DynamicRelationshipType.withName("isA"));



	                         for (OWLObjectPropertyExpression objectProperty: ontology.getObjectPropertiesInSignature()) {
	                              for(org.semanticweb.owlapi.reasoner.Node<OWLNamedIndividual>
	                              object: reasoner.getObjectPropertyValues(i,
	                              objectProperty)) {
	                                   String reltype = objectProperty.toString();
	                                   
	                                   //see reltype
	                                   System.out.println(reltype + "");
	                                   
	                                   reltype = reltype.substring(reltype.indexOf("#")+1,
	                                   reltype.lastIndexOf(">"));
	                                   String s =
	                                   object.getRepresentativeElement().toString();
	                                   s = s.substring(s.indexOf("#")+1,
	                                   s.lastIndexOf(">"));
	                                   Node objectNode =
	                                   getOrCreateNodeWithUniqueFactory(s,db);
	                                   individualNode.createRelationshipTo(objectNode,
	                                   DynamicRelationshipType.withName(reltype));
	                              }
	                        }
	                        for (OWLDataPropertyExpression dataProperty:
	                        ontology.getDataPropertiesInSignature()) {
	                              for (OWLLiteral object: reasoner.getDataPropertyValues(
	                              i, dataProperty.asOWLDataProperty())) {
	                                   String reltype =
	                                   dataProperty.asOWLDataProperty().toString();
	                                   
	                                   //see properties
	                                   System.out.println(reltype + "");
	                                   
	                                   reltype = reltype.substring(reltype.indexOf("#")+1, 
	                                   reltype.lastIndexOf(">"));
	                                   String s = object.toString();
	                                   individualNode.setProperty(reltype, s);
	                              }
	                         }
	                    }
	               }
	               tx.success();
	          } finally {
	               tx.finish();
	          }
	          
	        
	     }

	 public static void main(String[] args) throws Exception {
		 File file = new File("/Users/yalinzhang/neo4j/data/databases/graphPDO.db");
	        //Create a new Object of Graph Database
	        GraphDatabaseService graphDB = new GraphDatabaseFactory().newEmbeddedDatabase(file);
		try{
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File("/Users/yalinzhang/Ontology/pronto_transferowltojson/owldata/PDO.owl"));
		// File ontology = new File("/Users/yalinzhang/neo4j/data/databases/graph2.owl");
	        importOntology(ontology,graphDB);
		}finally{
			graphDB.shutdown(); 
		}
		registerShutdownHook(graphDB);
	 }
}


