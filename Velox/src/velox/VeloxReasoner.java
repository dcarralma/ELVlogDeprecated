package velox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLIrreflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLNegativeObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectHasSelf;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLReflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSameIndividualAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;
import org.semanticweb.owlapi.model.OWLTransitiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.SWRLArgument;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLRule;

import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.JRDFox.Prefixes;
import uk.ac.ox.cs.JRDFox.model.Individual;
import uk.ac.ox.cs.JRDFox.store.DataStore;
import uk.ac.ox.cs.JRDFox.store.Parameters;
import uk.ac.ox.cs.JRDFox.store.TupleIterator;

public class VeloxReasoner {

	private DataStore store;

	boolean satisfiableOntology;

	protected static String owlThingPred = "<http://www.w3.org/2002/07/owl#Thing>";
	protected static String owlNothingPred = "<http://www.w3.org/2002/07/owl#Nothing>";
	protected static String owlSameAsPred = "<http://www.w3http://www.w3.org/2002/07/owl#Nothing.org/2002/07/owl#sameAs>";
	protected static String owlNamedIndividualPred = "<http://www.w3.org/2002/07/owl#NamedIndividual>";

	private static String vX = "?VX";
	private static String vY = "?VY";
	private static String vZ = "?VZ";

	private int freshVarCounter = 0;
	private int freshConsCounter = 0;

	private Set<OWLObjectProperty> roles = new HashSet<OWLObjectProperty>();
	private Set<String> subRoleSelfRules = new HashSet<String>();
	private boolean containsSelf;

	private boolean satisfiable = false;

	public VeloxReasoner(String ontologyFilePath, String ontologyABoxDirPath) throws JRDFStoreException, IOException {
		store = new DataStore(DataStore.StoreType.SequentialHead);
		containsSelf = false;

		if (ontologyFilePath != null)
			loadOntology(ontologyFilePath);

		File ontologyABoxDir = new File(ontologyABoxDirPath);
		for (String ontologyABoxFile : ontologyABoxDir.list())
			if (ontologyABoxFile.endsWith(".owl") || ontologyABoxFile.endsWith(".ttl"))
				store.importTurtleFile(new File(ontologyABoxDirPath + File.separator + ontologyABoxFile));

		loadAuxSelfAndTopRules();
	}

	public void applyReasoning() throws JRDFStoreException {
		store.applyReasoning();
	}

	public void decideSatisfiability() throws JRDFStoreException {
		store.applyReasoning();
		TupleIterator tupleIterator = getIterator("SELECT DISTINCT ?x WHERE{ ?x " + Individual.RDF_TYPE + " " + owlNothingPred + " } ");
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

	private void loadOntology(String ontologyPath) throws JRDFStoreException {

		OWLOntology ontology = null;
		try {
			ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(ontologyPath));
		} catch (OWLOntologyCreationException e) {
			System.out.println(" > WARNING!!! OWLAPI Error loading the ontology: " + ontologyPath);
			e.printStackTrace();
			return;
		}

		for (OWLObjectProperty objectProperty : ontology.getObjectPropertiesInSignature())
			roles.add(objectProperty);

		for (OWLAxiom axiom : ontology.getAxioms()) {
			freshVarCounter = 0;
			switch (axiom.getAxiomType().toString()) {

			case "EquivalentClasses":
				// C1 equiv ... equiv Cn
				List<OWLClassExpression> equivConcepts = ((OWLEquivalentClassesAxiom) axiom).getClassExpressionsAsList();
				for (int i = 0; i < equivConcepts.size(); i++)
					for (int j = 0; j < equivConcepts.size(); j++)
						if (i != j)
							store.importRules(new VRule(expToAtoms(equivConcepts.get(i), vX, true), expToAtoms(equivConcepts.get(j), vX, false)).toOxRule());
				break;

			case "SubClassOf":
				// C sqsubseteq D
				OWLSubClassOfAxiom subClassOfAxiom = (OWLSubClassOfAxiom) axiom;
				store.importRules(new VRule(expToAtoms(subClassOfAxiom.getSuperClass(), vX, true), expToAtoms(subClassOfAxiom.getSubClass(), vX, false)).toOxRule());
				break;

			case "DisjointClasses":
				// C1 sqcap ... sqcap Cn sqsubseteq bot
				List<OWLClassExpression> disjConcepts = ((OWLDisjointClassesAxiom) axiom).getClassExpressionsAsList();
				for (int i = 0; i < disjConcepts.size(); i++)
					for (int j = i + 1; j < disjConcepts.size(); j++) {
						ArrayList<VAtom> bodyAtoms = new ArrayList<VAtom>();
						bodyAtoms.addAll(expToAtoms(disjConcepts.get(i), vX, false));
						bodyAtoms.addAll(expToAtoms(disjConcepts.get(j), vX, false));
						store.importRules(new VRule(new VAtom(owlNothingPred, vX), bodyAtoms).toOxRule());
					}
				break;

			case "ObjectPropertyDomain":
				// dom(R) sqsubseteq D
				OWLObjectPropertyDomainAxiom domainAxiom = (OWLObjectPropertyDomainAxiom) axiom;
				store.importRules(new VRule(expToAtoms(domainAxiom.getDomain(), vX, true), new VAtom((OWLObjectProperty) domainAxiom.getProperty(), vX, vY)).toOxRule());
				break;

			case "ObjectPropertyRange":
				// ran(R) sqsubseteq D
				OWLObjectPropertyRangeAxiom rangeAxiom = (OWLObjectPropertyRangeAxiom) axiom;
				store.importRules(new VRule(expToAtoms(rangeAxiom.getRange(), vY, true), new VAtom((OWLObjectProperty) rangeAxiom.getProperty(), vX, vY)).toOxRule());
				break;

			case "ReflexiveObjectProperty":
				// Ref(R)
				OWLObjectProperty reflexiveRole = (OWLObjectProperty) ((OWLReflexiveObjectPropertyAxiom) axiom).getProperty();
				String roleSelfConcept = roleToRoleSelfConcept(reflexiveRole);
				store.importRules(new VRule(new VAtom(roleSelfConcept, vX), new VAtom(owlThingPred, vX)).toOxRule());
				containsSelf = true;
				break;

			case "IrrefexiveObjectProperty":
				// Irref(R)
				OWLObjectProperty irreflexiveRole = (OWLObjectProperty) ((OWLIrreflexiveObjectPropertyAxiom) axiom).getProperty();
				store.importRules(new VRule(new VAtom(owlNothingPred, vX), new VAtom(roleToRoleSelfConcept(irreflexiveRole), vX)).toOxRule());
				containsSelf = true;
				break;

			case "EquivalentObjectProperties":
				// R equiv S
				List<OWLObjectPropertyExpression> equivRoles = new ArrayList<OWLObjectPropertyExpression>(((OWLEquivalentObjectPropertiesAxiom) axiom).getProperties());
				for (int i = 0; i < equivRoles.size(); i++)
					for (int j = 0; j < equivRoles.size(); j++)
						if (i != j) {
							OWLObjectProperty objectRolei = (OWLObjectProperty) equivRoles.get(i);
							OWLObjectProperty objectRolej = (OWLObjectProperty) equivRoles.get(j);
							store.importRules(new VRule(new VAtom(objectRolei, vX, vY), new VAtom(objectRolej, vX, vY)).toOxRule());
							subRoleSelfRules
									.add(new VRule(new VAtom(roleToRoleSelfConcept(objectRolei), vX), new VAtom(roleToRoleSelfConcept(objectRolej), vX)).toOxRule());
						}
				break;

			case "SubObjectPropertyOf":
				// R sqsubseteq S
				OWLSubObjectPropertyOfAxiom subObjectPropertyOfAxiom = (OWLSubObjectPropertyOfAxiom) axiom;
				OWLObjectProperty superRole = (OWLObjectProperty) subObjectPropertyOfAxiom.getSuperProperty();
				OWLObjectProperty subRole = (OWLObjectProperty) subObjectPropertyOfAxiom.getSubProperty();
				store.importRules(new VRule(new VAtom(superRole, vX, vY), new VAtom(subRole, vX, vY)).toOxRule());
				subRoleSelfRules.add(new VRule(new VAtom(roleToRoleSelfConcept(superRole), vX), new VAtom(roleToRoleSelfConcept(subRole), vX)).toOxRule());
				break;

			case "SubPropertyChainOf":
				// R1 o ... o Rn sqsubseteq S with n > 1
				OWLSubPropertyChainOfAxiom chainOfAxiom = (OWLSubPropertyChainOfAxiom) axiom;
				List<OWLObjectPropertyExpression> roleChain = chainOfAxiom.getPropertyChain();
				ArrayList<VAtom> bodyAtoms = new ArrayList<VAtom>();
				for (OWLObjectPropertyExpression chainedRole : roleChain)
					bodyAtoms.add(new VAtom((OWLObjectProperty) chainedRole, vX + freshVarCounter, vX + ++freshVarCounter));
				store.importRules(new VRule(new VAtom((OWLObjectProperty) chainOfAxiom.getSuperProperty(), vX + "0", vX + freshVarCounter), bodyAtoms).toOxRule());
				break;

			case "TransitiveObjectProperty":
				// Tran(R)
				String transitiveRoleR = ((OWLTransitiveObjectPropertyAxiom) axiom).getProperty().toString();
				store.importRules(new VRule(new VAtom(transitiveRoleR, vX, vY), new VAtom(transitiveRoleR, vX, vZ), new VAtom(transitiveRoleR, vZ, vY)).toOxRule());
				break;

			case "Rule":
				// SWRL Rule
				store.importRules(dlSafeRuleToRule((SWRLRule) axiom).toOxRule());
				break;

			case "ClassAssertion":
				// C(a)
				OWLClassAssertionAxiom classAssertion = (OWLClassAssertionAxiom) axiom;
				OWLClassExpression classExpressionC = classAssertion.getClassExpression();
				OWLIndividual individuala = classAssertion.getIndividual();
				if (!classExpressionC.isOWLThing()) {
					if (classExpressionC.getClassExpressionType().equals(ClassExpressionType.OWL_CLASS))
						store.addTriples(new VAtom((OWLClass) classExpressionC, individuala).toGroundTermFact());
					else if (classExpressionC.getComplementNNF().getClassExpressionType().equals(ClassExpressionType.OWL_CLASS)) {
						store.importRules(
								new VRule(new VAtom(owlNothingPred, individuala), new VAtom((OWLClass) classExpressionC.getComplementNNF(), individuala)).toOxRule());
					} else
						System.out.println("WARNING!!! Invalid ClassAssertion axiom at ELVNReasoner at ELVNReasoner.java." + "\n" + " > " + classAssertion + "\n" + " > "
								+ classExpressionC + "\n");
				}
				break;

			case "ObjectPropertyAssertion":
				// R(a, b)
				OWLObjectPropertyAssertionAxiom objectAssertion = (OWLObjectPropertyAssertionAxiom) axiom;
				store.addTriples(
						new VAtom((OWLObjectProperty) objectAssertion.getProperty(), objectAssertion.getSubject(), objectAssertion.getObject()).toGroundTermFact());
				break;

			case "NegativeObjectPropertyAssertion":
				// lnot R(a, b)
				OWLNegativeObjectPropertyAssertionAxiom negativeRoleAssertion = (OWLNegativeObjectPropertyAssertionAxiom) axiom;
				store.importRules(new VRule(new VAtom(owlNothingPred, negativeRoleAssertion.getSubject()),
						new VAtom((OWLObjectProperty) negativeRoleAssertion.getProperty(), negativeRoleAssertion.getSubject(), negativeRoleAssertion.getObject()))
								.toOxRule());
				break;

			case "SameIndividual":
				// a1 approx ... approx an
				OWLSameIndividualAxiom sameIndividualAxiom = (OWLSameIndividualAxiom) axiom;
				List<OWLIndividual> individualList = sameIndividualAxiom.getIndividualsAsList();
				for (int i = 0; i < individualList.size(); i++)
					for (int j = i + 1; j < individualList.size(); j++)
						store.addTriples(new VAtom(owlSameAsPred, individualList.get(i), individualList.get(j)).toGroundTermFact());

				break;

			case "DifferentIndividuals":
				// a not approx b
				OWLDifferentIndividualsAxiom differentIndividualAxiom = (OWLDifferentIndividualsAxiom) axiom;
				List<OWLIndividual> differentIndividualsList = differentIndividualAxiom.getIndividualsAsList();
				for (int i = 0; i < differentIndividualsList.size(); i++)
					for (int j = i + 1; j < differentIndividualsList.size(); j++)
						store.importRules(new VRule(new VAtom(owlNothingPred, differentIndividualsList.get(i)),
								new VAtom(owlSameAsPred, differentIndividualsList.get(i), differentIndividualsList.get(j))).toOxRule());
				break;

			case "Declaration":
				break;

			default:
				System.out.println("WARNING!!! Unrecognized type of axiom at ELVNReasoner(OWLOntology ontology) at ELVNReasoner.java." + "\n" + " > "
						+ axiom.getAxiomType().toString() + "\n" + " > " + axiom + "\n");
				break;
			}
		}
	}

	private VRule dlSafeRuleToRule(SWRLRule safeRule) {

		Set<SWRLAtom> safeBody = safeRule.getBody();
		Set<SWRLAtom> safeHead = safeRule.getHead();

		Set<SWRLAtom> safeAtoms = new HashSet<SWRLAtom>();
		safeAtoms.addAll(safeBody);
		safeAtoms.addAll(safeHead);

		HashMap<SWRLArgument, String> safeVarToXVarMap = new HashMap<SWRLArgument, String>();
		int freshVariableCounter = 1;
		for (SWRLAtom safeAtom : safeAtoms)
			for (SWRLArgument safeArgument : safeAtom.getAllArguments())
				if (safeArgument.toString().contains("Variable")) {
					if (safeVarToXVarMap.get(safeArgument) == null)
						safeVarToXVarMap.put(safeArgument, vX + Integer.toString(freshVariableCounter++));
				}

		ArrayList<VAtom> head = new ArrayList<VAtom>();
		for (SWRLAtom safeHeadAtom : safeHead)
			head.add(new VAtom(safeHeadAtom, safeVarToXVarMap));

		ArrayList<VAtom> body = new ArrayList<VAtom>();
		for (SWRLAtom safeBodyAtom : safeBody)
			body.add(new VAtom(safeBodyAtom, safeVarToXVarMap));
		for (String variable : safeVarToXVarMap.values())
			body.add(new VAtom(owlNamedIndividualPred, variable));

		return new VRule(head, body);
	}

	private ArrayList<VAtom> expToAtoms(OWLClassExpression conceptExpression, String term, boolean buildingHead) {

		ArrayList<VAtom> atoms = new ArrayList<VAtom>();

		switch (conceptExpression.getClassExpressionType().toString()) {

		case "ObjectIntersectionOf":
			// C1 scap ... sqcap Cn
			for (OWLClassExpression conjunctClassCi : conceptExpression.asConjunctSet())
				atoms.addAll(expToAtoms(conjunctClassCi, term, buildingHead));
			break;

		case "ObjectSomeValuesFrom":
			// exists R.C
			OWLObjectSomeValuesFrom existConceptRC = (OWLObjectSomeValuesFrom) conceptExpression;
			OWLClassExpression fillerC = existConceptRC.getFiller();
			String freshTerm;
			if (buildingHead) {
				freshTerm = "\"" + "cons" + ++freshConsCounter + "\"";
				if (!fillerC.isOWLThing() && !fillerC.toString().equals("<owl:Thing>"))
					atoms.add(new VAtom(owlThingPred, freshTerm));
			} else
				freshTerm = vX + ++freshVarCounter;
			atoms.add(new VAtom(existConceptRC.getProperty().toString(), term, freshTerm));
			atoms.addAll(expToAtoms(fillerC, freshTerm, buildingHead));
			break;

		case "ObjectOneOf":
			// {a1} sqcup ... sqcup {an}
			OWLObjectOneOf nominalConceptExpression = (OWLObjectOneOf) conceptExpression;
			if (nominalConceptExpression.getIndividuals().size() > 1)
				System.out.println("WARNING!!! Illegal OWLObjectOneOf at expressionToAtoms at ELVNReasoner.java." + "\n" + " > " + nominalConceptExpression + "\n");
			atoms.add(new VAtom(owlSameAsPred, nominalConceptExpression.getIndividuals().iterator().next().toString(), term));

		case "ObjectHasSelf":
			// exists R.Self
			containsSelf = true;
			String roleSelfConcept = roleToRoleSelfConcept((OWLObjectProperty) ((OWLObjectHasSelf) conceptExpression).getProperty());
			atoms.add(new VAtom(roleSelfConcept, term));

		case "ObjectHasValue":
			// exists R.{a}
			OWLObjectHasValue hasValueExpression = (OWLObjectHasValue) conceptExpression;
			atoms.add(new VAtom((OWLObjectProperty) hasValueExpression.getProperty(), term, hasValueExpression.getValue()));

		case "Class":
			// A
			atoms.add(new VAtom((OWLClass) conceptExpression, term));
			break;

		default:
			System.out.println("WARNING!!! Unrecognized type of concept expression at expressionToAtoms at ELVNReasoner.java." + "\n" + " > "
					+ conceptExpression.getClassExpressionType().toString() + "\n" + " > " + conceptExpression);
			break;
		}

		return atoms;
	}

	public void loadAuxSelfAndTopRules() throws IOException, JRDFStoreException {

		if (containsSelf) {
			for (OWLObjectProperty role : roles) {
				store.importRules(new VRule(new VAtom(roleToRoleSelfConcept(role), vX), new VAtom(role, vX, vX), new VAtom(owlNamedIndividualPred, vX)).toOxRule());
				store.importRules(new VRule(new VAtom(role, vX, vX), new VAtom(owlNamedIndividualPred, vX), new VAtom(roleToRoleSelfConcept(role), vX)).toOxRule());
			}

			for (String subRoleSelfRule : subRoleSelfRules)
				store.importRules(subRoleSelfRule);
		}

		store.importRules(new VRule(new VAtom(owlThingPred, vX), new VAtom(owlNamedIndividualPred, vX)).toOxRule());
	}

	private String roleToRoleSelfConcept(OWLObjectProperty role) {
		return role.toString().substring(0, role.toString().length() - 1) + "Self>";
	}

	public long getTriplesCount() throws JRDFStoreException {
		return store.getTriplesCount();
	}

}