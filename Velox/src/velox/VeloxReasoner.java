package velox;

import java.io.File;
import java.io.IOException;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.JRDFox.Prefixes;
import uk.ac.ox.cs.JRDFox.model.Individual;
import uk.ac.ox.cs.JRDFox.store.DataStore;
import uk.ac.ox.cs.JRDFox.store.Parameters;
import uk.ac.ox.cs.JRDFox.store.TupleIterator;

public class VeloxReasoner {

	private DataStore store;
	private boolean satisfiable = false;

	public VeloxReasoner(String ontologyFilePath, DataStore.StoreType storeType, int threads)
			throws JRDFStoreException, IOException {

		if (ontologyFilePath != null) {
			try {
				OWLOntology ontology = OWLManager.createOWLOntologyManager()
						.loadOntologyFromOntologyDocument(new File(ontologyFilePath));
				StoreInitializer mapper = new StoreInitializer();
				store = mapper.initializeStore(ontology, storeType, threads);

			} catch (OWLOntologyCreationException e) {
				System.out.println(" > WARNING!!! OWLAPI Error loading the ontology: " + ontologyFilePath);
				e.printStackTrace();
				store = null;
				return;
			}
		}
	}

	public void loadABoxTurtleFile(String ontologyABoxFilePath) throws JRDFStoreException {
		store.importTurtleFile(new File(ontologyABoxFilePath));
	}

	public void applyReasoning() throws JRDFStoreException {
		store.applyReasoning();
	}

	public void decideSatisfiability() throws JRDFStoreException {
		store.applyReasoning();
		TupleIterator tupleIterator = getIterator(
				"SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " " + SWURIs.owlNothing + " } ");
		if (tupleIterator.open() == 0)
			satisfiable = true;
		else
			satisfiable = false;
	}

	public boolean isSatisfiable() {
		return satisfiable;
	}

	public TupleIterator getIterator(String query) throws JRDFStoreException {
		return store.compileQuery(query, Prefixes.DEFAULT_IMMUTABLE_INSTANCE, new Parameters());
	}

	public long getTriplesCount() throws JRDFStoreException {
		return store.getTriplesCount();
	}

}