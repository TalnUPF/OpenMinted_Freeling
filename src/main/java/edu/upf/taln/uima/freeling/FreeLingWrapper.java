/*
OpenMinted_Freeling
Copyright (C) 2018  grup TALN - Universitat Pompeu Fabra
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/ 


package edu.upf.taln.uima.freeling;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ResourceMetaData;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;


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
import edu.upc.Jfreeling.ChartParser;
import edu.upc.Jfreeling.DepTree;
import edu.upc.Jfreeling.DepTreeler;
import edu.upc.Jfreeling.DepTxala;
import edu.upc.Jfreeling.HmmTagger;
import edu.upc.Jfreeling.LangIdent;
import edu.upc.Jfreeling.ListSentence;
import edu.upc.Jfreeling.ListSentenceIterator;
import edu.upc.Jfreeling.ListWord;
import edu.upc.Jfreeling.ListWordIterator;
import edu.upc.Jfreeling.Maco;
import edu.upc.Jfreeling.MacoOptions;
import edu.upc.Jfreeling.SWIGTYPE_p_splitter_status;
import edu.upc.Jfreeling.Splitter;
import edu.upc.Jfreeling.Tokenizer;
import edu.upc.Jfreeling.TreePreorderIteratorDepnode;
import edu.upc.Jfreeling.Util;
import edu.upc.Jfreeling.Word;

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
    private static final String DATA = FREELINGDIR + "share/freeling/";
    // this could be taken from config files...
    private static Set<String> TreelerLangs = new HashSet<String>(Arrays.asList("ca","de","en","es","pt","sl"));   
    private static Set<String> TxalaLangs = new HashSet<String>(Arrays.asList( "ca","en","es","as","gl" )); //removed "as" and "gl"
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

        System.loadLibrary("Jfreeling");
        Util.initLocale("default");
        getLogger().info("Freeling, autodetect mode: "+autodetect ); 

       if (!this.autodetect) {
           try {
            init(language);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new ResourceInitializationException();
        }
       } else {       
           lgid = new LangIdent(DATA + "common/lang_ident/ident.dat"); //ident-few for less languages!
        }


    }

    private void init(String lang) throws Exception
    {   
        if (tks.containsKey(lang))
            return; 
            // language already set       
        
        
        // read the configuration file
        Properties prop = new Properties();
        InputStream input = new FileInputStream(DATA+"config/"+lang+".cfg");
        prop.load(input);
 
         // Create options set for maco analyzer.
        // Default values are Ok, except for data files.
        MacoOptions op = new MacoOptions(lang);

        op.setDataFiles("", 
                DATA + "common/punct.dat", 
                prop.getProperty("DictionaryFile").replace("$FREELINGSHARE/", DATA).trim(),
                prop.getProperty("AffixFile").replace("$FREELINGSHARE/", DATA).trim(), 
                prop.getProperty("CompoundFile").replace("$FREELINGSHARE/", DATA).trim(),
                prop.getProperty("LocutionsFile").replace("$FREELINGSHARE/", DATA).trim(),
                prop.getProperty("NPDataFile").replace("$FREELINGSHARE/", DATA).trim(), 
                prop.getProperty("QuantitiesFile").replace("$FREELINGSHARE/", DATA).trim(),
                prop.getProperty("ProbabilityFile").replace("$FREELINGSHARE/", DATA).trim());
        

        
        tks.put(lang, new Tokenizer(DATA + lang + "/tokenizer.dat"));
        sps.put(lang, new Splitter(DATA + lang + "/splitter.dat"));
        sids.put(lang, sps.get(lang).openSession());

        Maco mf = new Maco(op);
        /*
        mf.setActiveOptions(false, true, true, true, // select which among created
                true, true, false, true, // submodules are to be used.
                true, true, true, true); // default: all created submodules
        */ 
        mf.setActiveOptions(
                false, // umap 
                prop.getProperty("NumbersDetection").trim().contentEquals("yes"),//   num,
                prop.getProperty("PunctuationDetection").trim().contentEquals("yes"),//    pun,
                prop.getProperty("DatesDetection").trim().contentEquals("yes"),//    dat,
                prop.getProperty("DictionarySearch").trim().contentEquals("yes"),//    dic,
                prop.getProperty("AffixAnalysis").trim().contentEquals("yes"),//    aff,
                prop.getProperty("CompoundAnalysis").trim().contentEquals("yes"),//    comp,
                true,//    rtk, // not found in properties.... 
                prop.getProperty("MultiwordsDetection").trim().contentEquals("yes"),//    mw,
                prop.getProperty("NERecognition").trim().contentEquals("yes"),//    ner,
                prop.getProperty("QuantitiesDetection").trim().contentEquals("yes"),//    qt,
                prop.getProperty("ProbabilityAssignment").trim().contentEquals("yes")//    prb                       
        );  
        
          
        mfs.put(lang, mf);
        // are used
        tgs.put(lang, new HmmTagger(DATA + lang + "/tagger.dat", true, 2));
        if (doDependency){
            if ((!useTxala) && (TreelerLangs.contains(lang)) ){
                getLogger().info("Freeling initating Treeler parser for "+lang ); 
                deps.put(lang,new DepTreeler(prop.getProperty("DepTreelerFile").replace("$FREELINGSHARE/", DATA)) );
             } else if (TxalaLangs.contains(lang)) {
                 
                getLogger().info("Freeling initating Txala parser for "+lang );
                parsers.put(lang,new ChartParser(
                        prop.getProperty("GrammarFile").replace("$FREELINGSHARE/", DATA) ));
                depTs.put(lang, new DepTxala( prop.getProperty("DepTxalaFile").replace("$FREELINGSHARE/", DATA),
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
    public void process(JCas cas)
        throws AnalysisEngineProcessException
    {
        String text = cas.getDocumentText();
        if (autodetect){
            language=cas.getDocumentLanguage();
            if (language==null || language.equalsIgnoreCase("x-unspecified")){
                // take the first 800 characters (200 words) Find what could be an end of sentence

                language = lgid.identifyLanguage(subStringForDetion(text));
                if (language.equals("none")){
                    getLogger().error("Freeling, error in language detection, skip document" );
                    return;
                }
                getLogger().info("Freeleing, the language detected for document is: " +language); 
                cas.setDocumentLanguage(language);

                cas.setDocumentLanguage(language);
            }
            try {
                init(language);
            }
            catch (Exception e) {
                getLogger().error("Freeling, error initializing language, skip document" );
                return;
            }       
           }
        process(cas, text.substring(0, text.length()), 0);
    }
            
    private String subStringForDetion(String text)
    {
        if (text.length()<1000) {
            // getLogger().info(" full text");
            return text;
        }
        // find the first end of sentence after the position 800
        // we look for a word in lower case followed by a dot.
        Pattern pattern = Pattern.compile("[a-z]+\\."); 
        Matcher match = pattern.matcher(text.substring(800));
        if (match.find()) {
            // getLogger().info(" found at pos" + match.end() + "text : " + text.substring(0, 800+match.end()-1));
            return text.substring(0, 800+match.end()-1);
        }               
        // not lucky              
        //getLogger().info(" no match");
        return text;
    }

    @Override
    protected void process(JCas cas, String line, int SentStart)
        throws AnalysisEngineProcessException
    {
          
        getLogger().info(" start processing from"+ SentStart + " size "+ line.length() );

        ListWord l = tks.get(language).tokenize(line);
        // Split the tokens into distinct sentences.
        // getLogger().info(" sentence split" );
       ls = sps.get(language).split(sids.get(language), l, false);
        // Perform morphological analysis
        //getLogger().info(" morpho" );
        mfs.get(language).analyze(ls);
        // Perform part-of-speech tagging.
        // getLogger().info(" POS" );
        tgs.get(language).analyze(ls);
        // Dependency parser
        //getLogger().info(" dependency" );
       boolean doDeps=doDependency;
        if (doDependency){  
            if (depTs.get(language)!=null){
                parsers.get(language).analyze( ls );             
                depTs.get(language).analyze(ls);
            } 
            else if (deps.get(language)!=null) {
                deps.get(language).analyze(ls);
            } else
                doDeps=false;
        }
        aJCas = cas;
        // getLogger().info(" export to UIMA" );
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
            getLogger().error("error on configuring map providers for that language, skip document" );
            return;
        }
        int begin;
        int end = 0;
        // Process every sentence.
        ListSentenceIterator sIt = new ListSentenceIterator(ls);
        while (sIt.hasNext()) {
            edu.upc.Jfreeling.Sentence s = sIt.next();
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
                //Token token = this.createToken(aJCas, start + begin, start + end);
                Token token = new Token(aJCas, start + begin, start + end);
                token.addToIndexes();
               try {
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
                 //   getLogger().info(" processing token from: " + (start + begin) +" to:" +(start + end)+ "  token:" + w.getForm() + " with POS tag: " + w.getTag() );
                    Type defposTagT=posMappingProvider.getTagType("*");
                    Type posTagT=posMappingProvider.getTagType(w.getTag());
                    int l=w.getTag().length()+1;
                    while(posTagT==defposTagT && --l>0){
                     posTagT = posMappingProvider.getTagType(w.getTag().substring(0, l)+"*");
                    }
                    POS posTag = (POS) cas.createAnnotation(posTagT, start + begin, start + end);
                    posTag.setPosValue(posTag.getPosValue());
                    posTag.setCoarseValue(w.getTag());
                    posTag.addToIndexes();
                    token.setPos(posTag);
                }
                catch (Exception e) {
                    
                     getLogger().error("error processing token from: " + (start + begin) +" to:" +(start + end)+ "  token:" + w.getForm() + " with POS tag: " + w.getTag() +"  " + e.getMessage() );
                     e.printStackTrace();
                     if (token==null)  getLogger().error("error token null");
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
                            Type depRel = depMappingProvider.getTagType(node.getLabel());
                            Dependency dep = (Dependency) cas.createFS(depRel);
                            dep.setGovernor(sourceToken);
                            dep.setDependent(targetToken);
                            dep.setDependencyType(node.getLabel());
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
