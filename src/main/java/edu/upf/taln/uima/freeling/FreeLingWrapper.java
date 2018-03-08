package edu.upf.taln.uima.freeling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ResourceMetaData;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import static org.apache.uima.util.Level.FINE;
import static org.apache.uima.util.Level.INFO;
import static org.apache.uima.util.Level.WARNING;

import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.pos.POSUtils;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProviderFactory;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.TokenForm;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.DependencyFlavor;
import edu.upc.freeling.*;

/**
 * Tokenizer and sentence splitter, POS tagger using FreeLing. parser is pending
 */
@ResourceMetaData(name = "Freeling_Parser")
@TypeCapability(outputs = { "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
        "de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS",
        "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma" })

public class FreeLingWrapper
    extends SegmenterBase
{
    /**
     * Use this language instead of the document language to resolve the model.
     */
    public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
    @ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false)
    protected String language;

    /**
     * Override the default variant used to locate the model.
     */
    public static final String PARAM_VARIANT = ComponentParameters.PARAM_VARIANT;
    @ConfigurationParameter(name = PARAM_VARIANT, mandatory = false)
    protected String variant;

    /**
     * Load the part-of-speech tag to UIMA type mapping from this location instead of locating the
     * mapping automatically.
     */
    public static final String PARAM_POS_MAPPING_LOCATION = ComponentParameters.PARAM_POS_MAPPING_LOCATION;
    @ConfigurationParameter(name = PARAM_POS_MAPPING_LOCATION, mandatory = false)
    protected String POSMappingLocation;
    /**
     * Load the dependency functions to UIMA type mapping from this location instead of locating the
     * mapping automatically.
     */
    public static final String PARAM_DEPENDENCY_MAPPING_LOCATION = ComponentParameters.PARAM_DEPENDENCY_MAPPING_LOCATION;
    @ConfigurationParameter(name = PARAM_DEPENDENCY_MAPPING_LOCATION, mandatory = false)
    protected String DependencyMappingLocation;

    /**
     * Load the ner tags to UIMA type mapping from this location instead of locating the mapping
     * automatically.
     */
    public static final String PARAM_NAMED_ENTITY_MAPPING_LOCATION = ComponentParameters.PARAM_NAMED_ENTITY_MAPPING_LOCATION;
    @ConfigurationParameter(name = PARAM_NAMED_ENTITY_MAPPING_LOCATION, mandatory = false)
    protected String NamedEntityMappingLocation;

    /**
     * Do dependency parsing, it can be changed if there is no available model.
     */
    public static final String PARAM_DO_DEPENDECY_PARSING =  "doDependency";
    @ConfigurationParameter(name = PARAM_DO_DEPENDECY_PARSING, mandatory = true)
    protected Boolean doDependency;
    
    
    /**
     * Load the ner tags to UIMA type mapping from this location instead of locating the mapping
     * automatically.
     */
    public static final String PARAM_USE_RULE_BASED = "useTxala";
    @ConfigurationParameter(name = PARAM_USE_RULE_BASED, mandatory = false, defaultValue="false")
    protected Boolean useTxala;
    
    /**
     * Load the ner tags to UIMA type mapping from this location instead of locating the mapping
     * automatically.
     */
    public static final String PARAM_LANGUAGE_AUTODETECT = "autodetect";
    @ConfigurationParameter(name = PARAM_LANGUAGE_AUTODETECT, mandatory = false, defaultValue="false")
    protected Boolean autodetect;
    
    
    // Freeling elements some of should be parameters...
    private static final String FREELINGDIR = "/usr/local/";
    private static final String DATA = FREELINGDIR + "/share/freeling/";
    private static Set<String> TreelerLangs = new HashSet<String>(Arrays.asList("ca","de","en","es","hr","pt","sl"));   
    private static Set<String> TxalaLangs = new HashSet<String>(Arrays.asList("as","ca","en","es","gl"));
    private LangIdent lgid; 

    private HashMap<String, Tokenizer> tks= new HashMap<>();
    private HashMap<String, Splitter> sps= new HashMap<>();
    private HashMap<String, SWIGTYPE_p_splitter_status > sids= new HashMap<>();
    private HashMap<String, Maco> mfs= new HashMap<>();
    private HashMap<String, HmmTagger> tgs= new HashMap<>();
    private HashMap<String, ChartParser>  parsers= new HashMap<>();
    private HashMap<String, DepTxala > depTs= new HashMap<>() ;
    private HashMap<String,DepTreeler> deps= new HashMap<>();
    private ListSentence ls;
    

    private MappingProvider posMappingProvider;
    private MappingProvider depMappingProvider;
   private JCas aJCas;
   


    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException
    {
        super.initialize(aContext);
        // Modify this line to be your FreeLing installation directory
        System.loadLibrary("freeling_javaAPI");
        Util.initLocale("default");
        getLogger().info("Freeling, autodetect mode: "+autodetect ); 

       if (!this.autodetect) {
           init(language);
       } else {       
           lgid = new LangIdent(DATA + "common/lang_ident/ident.dat"); //ident-few for less languages!
        }


    }

    private void init(String lang)
    {   
        if (tks.containsKey(lang)) return; 
         // language already set       
        // Create options set for maco analyzer.
        // Default values are Ok, except for data files.
        MacoOptions op = new MacoOptions(lang);

        op.setDataFiles("", DATA + "common/punct.dat", DATA + lang + "/dicc.src",
                DATA + lang + "/afixos.dat", "", DATA + lang + "/locucions.dat",
                (Files.exists(Paths.get(DATA + lang + "/np.dat"))? DATA + lang + "/np.dat":""), 
                (Files.exists(Paths.get(DATA + lang + "/quantities.dat"))? DATA + lang + "/quantities.dat":""),
                DATA + lang + "/probabilitats.dat");
        tks.put(lang, new Tokenizer(DATA + lang + "/tokenizer.dat"));
        sps.put(lang, new Splitter(DATA + lang + "/splitter.dat"));
        sids.put(lang, sps.get(lang).openSession());

        Maco mf = new Maco(op);
        mf.setActiveOptions(false, true, true, true, // select which among created
                true, true, false, true, // submodules are to be used.
                true, true, true, true); // default: all created submodules
        mfs.put(lang, mf);
        // are used
        tgs.put(lang, new HmmTagger(DATA + lang + "/tagger.dat", true, 2));
        if (doDependency){
            if ((!useTxala) && (TreelerLangs.contains(lang)) ){
                getLogger().info("Freeling initating Treeler parser for "+lang ); 
                deps.put(lang,new DepTreeler(DATA + lang + "/dep_treeler/dependences.dat") );
             } else if (TxalaLangs.contains(lang)) {
                getLogger().info("Freeling initating Txala parser for "+lang );
                parsers.put(lang,new ChartParser(
                        DATA + lang + "/chunker/grammar-chunk.dat" ));
                depTs.put(lang, new DepTxala( DATA + lang + "/dep_txala/dependences.dat",
                        parsers.get(lang).getStartSymbol() ) );
            } 
        }
        // UIMA mapping providers

        posMappingProvider = MappingProviderFactory.createPosMappingProvider(POSMappingLocation,
                "eagle", lang);
        depMappingProvider = MappingProviderFactory
                .createDependencyMappingProvider(DependencyMappingLocation, "freeling", lang);
        
    }

    @Override
    protected void process(JCas cas, String line, int SentStart)
        throws AnalysisEngineProcessException
    {
        
        if (autodetect){
            // language=cas.getDocumentLanguage();
            language = lgid.identifyLanguage(line);
            if (language.equals("none")){
                getLogger().error("Freeling, error in language detection, skip document" );
                return;
            }
            getLogger().info("Freeleing, the language detected for document is: " +language); 
            cas.setDocumentLanguage(language);
            init(language);
        }
       
        ListWord l = tks.get(language).tokenize(line);
        // Split the tokens into distinct sentences.
        ls = sps.get(language).split(sids.get(language), l, false);
        // Perform morphological analysis
        mfs.get(language).analyze(ls);
        // Perform part-of-speech tagging.
        tgs.get(language).analyze(ls);
        // Dependency parser
        boolean doDeps=doDependency;
        if (doDependency){  
            if (depTs.get(language)!=null){
                depTs.get(language).analyze(ls);
            } 
            else if (deps.get(language)!=null) {
                deps.get(language).analyze(ls);
            } else
                doDeps=false;
        }
        aJCas = cas;
        exportToUIMA(ls, SentStart,doDeps);
    }

    private void exportToUIMA(ListSentence ls, int start,Boolean doDeps)
    {

        CAS cas = aJCas.getCas();
        try {
            posMappingProvider.configure(cas);
            depMappingProvider.configure(cas);
         }
        catch (AnalysisEngineProcessException e1) {
            // TODO Auto-generated catch block
            // use the default log system?
            e1.printStackTrace();
        }
        int begin;
        int end = 0;
        // Process every sentence.
        ListSentenceIterator sIt = new ListSentenceIterator(ls);
        while (sIt.hasNext()) {
            edu.upc.freeling.Sentence s = sIt.next();
            // add sentence
            int sBegin = 0;
            Boolean first = true;
            Token[] tokens = new Token[(int) s.size()];
            int i = 0;
            ListWordIterator wIt = new ListWordIterator(s);
            // iterate over tokens
            while (wIt.hasNext()) {
                Word w = wIt.next();
                begin = (int) w.getSpanStart();
                if (first) {
                    first = false;
                    sBegin = begin;
                }
                end = (int) w.getSpanFinish();
                // create token
                Token token = this.createToken(aJCas, start + begin, start + end);
                //create  lema
                Lemma lemma = new Lemma(aJCas, start + begin, start + end);
                lemma.setValue(w.getLemma());
                lemma.addToIndexes();
                token.setLemma(lemma);
                //create  form
                TokenForm f = new TokenForm(aJCas, start + begin, start + end);
                f.setValue(w.getForm());
                token.setForm(f);
                //create  POS
                try {
                    Type defposTagT=posMappingProvider.getTagType("*");
                    Type posTagT=posMappingProvider.getTagType(w.getTag());
                    int l=w.getTag().length();
                    while(posTagT==defposTagT && --l>0){
                     posTagT = posMappingProvider.getTagType(w.getTag().substring(0, l)+"*");
                    }
                    POS posTag = (POS) cas.createAnnotation(posTagT, start + begin, start + end);
                    posTag.setPosValue(w.getTag());
                    POSUtils.assignCoarseValue(posTag);
                    posTag.addToIndexes();
                    token.setPos(posTag);
                }
                catch (Exception e) {
                     getLogger().error("error processing token "+ w.getForm() + " with POS tag" + w.getTag());
                }
                tokens[i++] = token;
            } //end for tokens
                //Add dependencies.       
                if (doDeps){  
                    DepTree dtree = s.getDepTree(s.getBestSeq());
                    for (int n = 0; n < s.size(); n++) {
                        TreePreorderIteratorDepnode node = dtree.getNodeByPos((long) n);               
                        if (node.isRoot() || node.getParent().getLabel().equals("VIRTUAL_ROOT")) {
                            Token rootToken = tokens[n];
                            Dependency dep = new Dependency(aJCas);
                            dep.setGovernor(rootToken);
                            dep.setDependent(rootToken);
                            dep.setDependencyType("ROOT");
                            dep.setFlavor(DependencyFlavor.BASIC);
                            dep.setBegin(dep.getDependent().getBegin());
                            dep.setEnd(dep.getDependent().getEnd());
                            dep.addToIndexes();
                        }
                        else {
                            Token sourceToken = tokens[(int) node.getParent().getWord().getPosition()];
                            Token targetToken = tokens[n];
                            Type depRel = depMappingProvider.getTagType(node.getParent().getLabel());
                            Dependency dep = (Dependency) cas.createFS(depRel);
                            dep.setGovernor(sourceToken);
                            dep.setDependent(targetToken);
                            dep.setDependencyType(node.getParent().getLabel());
                            dep.setFlavor(DependencyFlavor.BASIC);
                            dep.setBegin(dep.getDependent().getBegin());
                            dep.setEnd(dep.getDependent().getEnd());
                            dep.addToIndexes();
                        }
                    }
                createSentence(aJCas, start + sBegin, start + end);
        }
      }
    }
}
