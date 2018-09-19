package velox;

import java.io.File;
import java.io.IOException;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import uk.ac.ox.cs.JRDFox.JRDFoxException;
import uk.ac.ox.cs.JRDFox.Prefixes;
import uk.ac.ox.cs.JRDFox.model.Individual;
import uk.ac.ox.cs.JRDFox.store.DataStore;
import uk.ac.ox.cs.JRDFox.store.TupleIterator;

public class VeloxReasoner {

	private DataStore store;
	private boolean satisfiable = false;

	public VeloxReasoner(String ontologyFilePath, File[] turtleFiles, DataStore.StoreType storeType, int threads) throws IOException, JRDFoxException {

		if (ontologyFilePath != null) {
			try {
				OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(ontologyFilePath));
				StoreInitializer storeInitalizer = new StoreInitializer();
				store = storeInitalizer.initializeStore(ontology, storeType, threads);
				store.importFiles(turtleFiles);
			} catch (OWLOntologyCreationException e) {
				System.out.println(" > WARNING!!! OWLAPI Error loading the ontology: " + ontologyFilePath);
				e.printStackTrace();
				store = null;
				return;
			}
		}
	}

	public void applyReasoning() throws JRDFoxException {
		store.applyReasoning();
	}

	public void decideSatisfiability() throws JRDFoxException {
		store.applyReasoning();
		TupleIterator tupleIterator = getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " " + SWURIs.owlNothing + " } ");
		if (tupleIterator.open() == 0)
			satisfiable = true;
		else
			satisfiable = false;
	}

	public boolean isSatisfiable() {
		return satisfiable;
	}

	public TupleIterator getIterator(String query) throws JRDFoxException {
		return store.compileQuery(query, Prefixes.DEFAULT_IMMUTABLE_INSTANCE);
	}

	public long getTriplesCount() throws JRDFoxException {
		return store.getTriplesCount();
	}

}