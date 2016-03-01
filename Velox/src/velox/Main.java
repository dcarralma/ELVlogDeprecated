package velox;

import java.io.IOException;

import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.JRDFox.model.Individual;
import uk.ac.ox.cs.JRDFox.store.TupleIterator;

public class Main {

	public static void main(String[] arguments) throws OWLOntologyCreationException, OWLOntologyStorageException, IOException, JRDFStoreException {

		System.out.println("Arguments: " + arguments[0] + " " + arguments[1] + "\n");

		String ontologyTBoxFilePath = arguments[0];
		String ontologyABoxDirectoryPath = arguments[1];

		long start = System.nanoTime();
		System.out.println("  > Initializing Reasoner");
		VeloxReasoner velox = new VeloxReasoner(ontologyTBoxFilePath, ontologyABoxDirectoryPath);
		System.out.println("   * Files Loaded (" + velox.getTriplesCount() + "): " + ((System.nanoTime() - start) / 1000000000) + "s");

		System.out.println("  > Reasoning");
		start = System.nanoTime();
		velox.decideSatisfiability();
		System.out.println("   * Satisfiable ontology: " + velox.isSatisfiable());
		System.out.println("   * Materialization Complete (" + velox.getTriplesCount() + "): " + ((System.nanoTime() - start) / 1000000000) + "s");

		System.out.println("  > Query Results: "
				+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " <http://www.biopax.org/release/biopax-level3.owl#Query1> } ")) + " / "
				+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " <http://www.biopax.org/release/biopax-level3.owl#Query2> } ")) + " / "
				+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " <http://www.biopax.org/release/biopax-level3.owl#Query3> } ")) + " / "
				+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " <http://www.biopax.org/release/biopax-level3.owl#Query4> } ")) + " / "
				+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " <http://www.biopax.org/release/biopax-level3.owl#Query5> } ")) + " / "
				+ size(velox.getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " <http://www.biopax.org/release/biopax-level3.owl#Query6> } ")));

		System.out.println("\n");
	}

	private static int size(TupleIterator tupleIterator) throws JRDFStoreException {
		int counter = 0;
		for (long multiplicity = tupleIterator.open(); multiplicity != 0; multiplicity = tupleIterator.getNext())
			counter++;
		return counter;
	}
}
