package org.ihtsdo.termserver.scripting.creation;

import java.util.ArrayList;
import java.util.List;

/**
 * We will need about 50 new Structure concepts for hair follicle. 
 * Please see the attached disorder concepts that have been modelled by hair follicle structure. 
 * All new structure concepts should have Entire concepts as their subtype. It means this task 
 * will have 100 new anatomy concepts. I would recommend this task to be completed before any 
 * changes to disorders. I am happy to review the task to ensure they are correctly modeled. 
 * 
 * The following is the IS A modeling pattern. A new structure should have at least two parent
 *  concepts, one Entire concept, and/or other new subconcepts hair follicle.
 *  
 *  Skin structure of X (body structure)
 *  Structure of hair follicle of Y (body structure)  -  Y is the direct super concept region of X, e.g. upper limb structure is super concept of upper arm.
 *   Structure of hair follicle of X (body structure)
 *      Entire hair follicle of X (body structure)

For your example |Structure of hair follicle of upper arm (body structure)|:

371309009 |Skin structure of upper arm (body structure)|
xxxxxx |Structure of hair follicle of upper limb (body structure)|
  xxxxxx |Entire hair follicle of upper limb (body structure)|
  xxxxxxx |Structure of hair follicle of upper arm (body structure)|
    xxxxxx |Entire hair follicle of upper arm (body structure)|
 */
public class HairFollicleCreator extends ConceptCreator {
	
	private static HairFollicleCreator singleton = null;
	
	static {
		ConceptCreator creator = getHairFollicleCreator();
		ConceptCreationSupervisor.getSupervisor().registerCreator(creator);
		
		ConceptCreationPattern pattern = ConceptCreationPattern
				.define()
				.withTerm("Structure of hair follicle of [X]")
				.withSemTag(SEMTAG_BODY);
		
		ConceptCreationPattern parent1 = ConceptCreationPattern
				.define()
				.withTerm("Structure of hair follicle of [Y]")
				.withSemTag(SEMTAG_BODY)
				.withStrategy(Strategy.ImmediateParentOfX);
		
		ConceptCreationPattern parent2 = ConceptCreationPattern
				.define()
				.withTerm("Skin structure of [X]")
				.withSemTag(SEMTAG_BODY);
		
		ConceptCreationPattern child = ConceptCreationPattern
				.define()
				.withTerm("Entire hair follicle of [X]")
				.withSemTag(SEMTAG_BODY);
		
		pattern.addParentPattern(parent1)
			.addParentPattern(parent2)
			.addChildPattern(child);
	}
	
	
	public static HairFollicleCreator getHairFollicleCreator() {
		if (singleton == null) {
			singleton = new HairFollicleCreator();
			singleton.addInspiration("67290009 |Hair follicle structure (body structure)|");
			singleton.addInspiration("127856007 |Skin and/or subcutaneous tissue structure (body structure)|");
		}
		return singleton;
	}

}
