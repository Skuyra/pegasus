/*
 * This file or a portion of this file is licensed under the terms of
 * the Globus Toolkit Public License, found in file GTPL, or at
 * http://www.globus.org/toolkit/download/license.html. This notice must
 * appear in redistributions of this file, with or without modification.
 *
 * Redistributions of this Software, with or without modification, must
 * reproduce the GTPL in: (1) the Software, or (2) the Documentation or
 * some other similar material which is provided with the Software (if
 * any).
 *
 * Copyright 1999-2004 University of Chicago and The University of
 * Southern California. All rights reserved.
 */
package org.griphyn.cPlanner.parser;


import org.griphyn.cPlanner.classes.Data;
import org.griphyn.cPlanner.classes.PCRelation;
import org.griphyn.cPlanner.classes.PegasusFile;
import org.griphyn.cPlanner.classes.SubInfo;

import org.griphyn.cPlanner.common.LogManager;
import org.griphyn.cPlanner.common.PegasusProperties;

import org.griphyn.cPlanner.namespace.Condor;
import org.griphyn.cPlanner.namespace.Dagman;
import org.griphyn.cPlanner.namespace.ENV;
import org.griphyn.cPlanner.namespace.Globus;
import org.griphyn.cPlanner.namespace.Hints;
import org.griphyn.cPlanner.namespace.Namespace;
import org.griphyn.cPlanner.namespace.VDS;

import org.griphyn.cPlanner.parser.dax.Callback;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Set;
import java.util.HashSet;

/**
 * This class parses the XML file whichis generated by Abstract Planner and ends
 * up making an ADag object which contains theinformation to make the Condor
 * submit files. The parser used to parse the file is Xerces.
 *
 * @author Karan Vahi
 * @author Gaurang Mehta
 * @version $Revision$
 *
 * @see org.griphyn.cPlanner.classes.SubInfo
 * @see org.griphyn.cPlanner.classes.DagInfo
 * @see org.griphyn.cPlanner.classes.ADag
 * @see org.griphyn.cPlanner.classes.PCRelation
 */

public class DaxParser extends Parser {

    /**
     * The "not-so-official" location URL of the DAX schema definition.
     */
    public static final String SCHEMA_LOCATION =
                                 "http://pegasus.isi.edu/schema/dax-2.0.xsd";

    /**
     * URI namespace
     */
    public static final String SCHEMA_NAMESPACE =
                                           "http://pegasus.isi.edu/schema/DAX";

    public String mDaxSchemaVersion;


    /**
     * A boolean variable set to true when we have got all the logical filenames.
     * After this all the filename tags are not added in Vector mLogicalFilesInADag
     * This is because the DAX file specifies all the input and output files
     * in the starting, and then in the job tags also the filename tags are nested.
     */
    private boolean infoAboutAllFilesRecv = false;

    /**
     * The handle to the class implementing the callback interface.
     */
    private Callback mCallback;

    /**
     * For holding the key attribute in profile tag.
     */
    private String mProfileKey = new String();

    /**
     * For holding the environment variables if specified in the Profile Element.
     *
     */
    private ENV mEnvNS;


    /**
     * Holds the extra options for Condor which maybe specified in the profile tags
     * with namespace Condor.
     */
    private Condor mCondorNS;

    /**
     * Objects that handle the various namespaces. For generating the rslstring
     * if specified.
     */
    private Globus mGlobusNS;

    /**
     * Holds the information got in the profile tag for hint namespace.
     */
    private Hints mHintNS;

    /**
     * Holds the information got in the profile tag for vds namespace.
     */
    private VDS mVdsNS;

    /**
     * Holds the information got in the profile tag for dagman namespace.
     */
    private Dagman mDagmanNS;

    /**
     * For holding the namespace if specified in the Profile Element.
     */
    private String mNamespace = new String();

    /**
     * Set as and when Profile and Argument tags are started and ended.
     * Need to in order to determine the nested filename tags which may appear in
     * these elements.
     */
    private boolean mProfileTag = false;
    private boolean mArgumentTag = false;



    /**
     * These store the current child element for the child parent relationship.
     * We get nested parent elements in a child element. Hence the child remains
     * the same while the parent id for the relationship varies.
     */
    private String mCurrentChildId = new String();

    /**
     * The list of parents of a node referred to by mCurrentChildId.
     */
    private List mParents;


    /**
     * Holds information regarding the current job being parsed. It's scope can
     * be seen as the job element.
     */
    private SubInfo mCurrentJobSubInfo = new SubInfo();



    /**
     * All the arguments to a particular job.
     */
    private String mWholeCommandString = new String();

    /**
     * Holds the input files for a particular job making the aDag. They are Vector
     * of PegasusFile Objects which store the transiency information of each
     * logical file.
     *
     * @see org.griphyn.cPlanner.classes.PegasusFile
     */
    private Set mVJobInpFiles = new HashSet();

    /**
     * Holds the output files for a particular job making the aDag.
     * They are vector of PegasusFile Objects which store the transiency
     * information of each logical file.
     *
     * @see org.griphyn.cPlanner.classes.PegasusFile
     */
    private Set mVJobOutFiles = new HashSet();

    /**
     * The default constructor
     *
     * @param properties the <code>PegasusProperties</code> to be used.
     */
    public DaxParser( PegasusProperties properties ) { //default constructor
        super( properties );
        mGlobusNS = new Globus();
        mCondorNS = new Condor();
        mEnvNS    = new ENV();
        mDagmanNS = new Dagman();
        mHintNS   = new Hints();
        mVdsNS    = new VDS();


    }

    /**
     * The constructor initialises the parser, and turns on the validation feature
     * in Xerces.
     *
     * @param daxFileName the file which you want to parse.
     * @param properties the <code>PegasusProperties</code> to be used.
     * @param callback    the object which implements the callback.
     */
    public DaxParser(String daxFileName, PegasusProperties properties, Callback callback) {
        super( properties );

        try{
            this.testForFile(daxFileName);
        }
        catch( Exception e){
            throw new RuntimeException( e );
        }

        //try to get the version number
        //of the dax
        mDaxSchemaVersion = getVersionOfDAX(daxFileName);

        String schemaLoc = getSchemaLocation();
        mLogger.log("Picking schema for DAX" + schemaLoc,
                    LogManager.CONFIG_MESSAGE_LEVEL);
        String list = DaxParser.SCHEMA_NAMESPACE + " " + schemaLoc;
        setSchemaLocations(list);

        mLogger.log("Parsing the DAX",LogManager.INFO_MESSAGE_LEVEL);

        mCurrentJobSubInfo.condorUniverse = "vanilla"; //default value

        //initialising the namespace handles
        mCondorNS = new Condor();
        mEnvNS    = new ENV();
        mGlobusNS = new Globus();
        mDagmanNS = new Dagman();
        mHintNS   = new Hints();
        mVdsNS    = new VDS();

        mCallback = callback;

        startParser(daxFileName);
        mLogger.logCompletion("Parsing the DAX",LogManager.INFO_MESSAGE_LEVEL);
    }

    /**
     * This starts the parsing of the file by the parser.
     *
     * @param daxFileName    the path/uri to the XML file you want to parse.
     */
    public void startParser(String daxFileName) {
        try {
            mParser.parse(daxFileName);
        }
        catch (Exception e) {
            //if a locator error then
            String message = (mLocator == null) ?
                           "While parsing the file " + daxFileName:
                           "While parsing file " + mLocator.getSystemId() +
                           " at line " + mLocator.getLineNumber() +
                           " at column " + mLocator.getColumnNumber();
            throw new RuntimeException(message, e);
        }

    }

    /**
     * Overriding the empty implementation provided by
     * DefaultHandler of ContentHandler. This receives the notification
     * from the sacks parser when start tag of an element comes
     */
    public void startElement(String uri, String local, String raw,
                             Attributes attrs) throws SAXException {

        //setting the command line option only if textContent > 0
        if (mTextContent.length() > 0) {
            mWholeCommandString = mWholeCommandString.concat(new String(
                mTextContent));
            //System.out.println("\n Text Content is:" + new String(mTextContent));
            //resetting the buffer
            mTextContent.setLength(0);
        }

        //dealing with ADag tag
        if (local.trim().equalsIgnoreCase("adag")) {
            handleAdagTagStart(local, attrs);
        }

        //dealing with filename tags
        else if (local.trim().equalsIgnoreCase("filename")) {
            handleFilenameTagStart(local, attrs);
        }

        //dealing with the uses tag July 18
        else if (local.trim().equalsIgnoreCase("uses")) {
            handleUsesTagStart(local, attrs);
        }

        //dealing with the job tags
        else if (local.trim().equalsIgnoreCase("job")) {
            handleJobTagStart(local, attrs);
        }

        //dealing with profile tag
        else if (local.trim().equalsIgnoreCase("profile")) {
            handleProfileTagStart(local, attrs);
        }

        //dealing with the making of parent child relationship pairs
        else if (local.trim().equalsIgnoreCase("child")) {
            handleChildTagStart(local, attrs);
        }
        else if (local.trim().equalsIgnoreCase("parent")) {
            handleParentTagStart(local, attrs);
        }

        //dealing with the start of argument tag
        else if (local.trim().equalsIgnoreCase("argument")) {
            handleArgumentTagStart(local, attrs);
        }

        //dealing with stdout for current job
        else if (local.trim().equalsIgnoreCase("stdout")) {
            handleStdoutTagStart(local, attrs);
        }

        //dealing with stdin for current job
        else if (local.trim().equalsIgnoreCase("stdin")) {
            handleStdinTagStart(local, attrs);
        }

        //dealing with stderr for current job
        else if (local.trim().equalsIgnoreCase("stderr")) {
            handleStderrTagStart(local, attrs);
        }

    }

    /**
     * A convenience method that tries to determine the version of the dax
     * schema by reading ahead in the DAX file, and searching for
     * the version attribue in the file.
     *
     * @param file the name of the dax file.
     */
    public String getVersionOfDAX(String file){
        String schema = getSchemaOfDocument(file);
        return extractVersionFromSchema(schema);

    }

    /**
     * Determines the version of the DAX as specified in a schema string.
     *
     * @param schema   the schema string as specified in the root element of
     *                 the DAX.
     *
     * @return  the version.
     */
    public String extractVersionFromSchema(String schema){
        String token = null;
        String version = null;

        if(schema == null)
            return null;

        StringTokenizer st = new StringTokenizer(schema);
        while(st.hasMoreTokens()){
            token = st.nextToken();
            if(token.endsWith(".xsd")){
                //we got our match
                String name = new File(token).getName();
                int p1 = name.indexOf("dax-");
                int p2 = name.lastIndexOf(".xsd");
                //extract the version number
                version = (( p1 > -1) && (p2 > -1))?name.substring(p1+4,p2):null;
                return version;

            }
        }

        mLogger.log("Could not find the version number in DAX schema name",
                    LogManager.WARNING_MESSAGE_LEVEL);
        return version;

    }

    /**
     * A convenience method that tries to get the name of the schema the document
     * refers to. It returns the value of the xsi:schemaLocation.
     *
     * @param file the name of the dax file.
     */
    public String getSchemaOfDocument(String file){
        StringTokenizer st = null;
        String key = null;
        String value = null;

        try{
            BufferedReader in = new BufferedReader(new FileReader(file));
            String line = null;
            int p1 , p2 , c = 0;

            while ( (line = (in.readLine()).trim()) != null) {

                if(c == 0){
                    //try to check if it is an xml file
                    if ( ( (p1 = line.indexOf("<?xml")) > -1) &&
                         ( (p2 = line.indexOf("?>", p1)) > -1) ) {
                            //xml file is valid.
                            c++;

                    }
                    else{
                        //throw a exception
                        throw new java.lang.RuntimeException("Dax File is not xml " + file);
                    }

                }
                else{
                    if( (p1 = line.indexOf("<adag")) > -1){
                        line = line.substring(p1 + "<adag".length());
                        c++;

                    }
                    else{
                        if(c < 2)
                            //goto next line
                            continue;
                    }

                    st = new StringTokenizer(line,"= \"");
                    while(st.hasMoreTokens()){
                        c++;
                        if(c%2 == 1){
                            key = st.nextToken().trim();
                        }
                        else{
                            if(key.equalsIgnoreCase("xsi:schemaLocation")){
                                value = st.nextToken("=\"");
                                return value;
                            }
                            else{
                                value = st.nextToken();
                            }


                        }

                    }
                }
            }
        }
        catch(java.io.IOException e){
            mLogger.log("Parsing the dax file for version number " + " :" +
                        e.getMessage(),LogManager.ERROR_MESSAGE_LEVEL);

        }
        return null;

    }


    /**
     * Invoked when the starting of the adag element is got.
     * Information received is
     * name :  the name of the ADag
     * count:  Chimera can generate multiple abstract dags for a request.
     * index:  what is the index of the ADag being passed. Should
     *         vary between 0 and count - 1.
     */
    private void handleAdagTagStart(String local, Attributes attrs) {
        HashMap mp = new HashMap();
        String key;
        String value;

        for(int i = 0; i < attrs.getLength(); i++){
            key = attrs.getLocalName(i);
            value = attrs.getValue(i);
            //should probably check for valid attributes before setting
            mp.put(key,value);
            //System.out.println(key + " --> " + value);
        }
        //call the callback interface
        mCallback.cbDocument(mp);




    }


    /**
     * Invoked when the starting of the filename element is got.
     */
    private void handleFilenameTagStart(String local, Attributes attrs) {
        String linkType = new String(); //holds the link info about a logical file corr to a job
        String fileName = new String();
        String isTemp = new String();

        fileName = attrs.getValue("", "file").trim();

        PegasusFile pf = new PegasusFile(fileName);

        if (!infoAboutAllFilesRecv) {
            //this means we are dealing with filename tags in
            //the starting of the dax. These tags
            //contain the linkage information
            //logicalFilesInADag.addElement(fileName);

            /*
            Now linkage information is only gotten from the individual jobs.

            linkType = attrs.getValue("", "link").trim();

            String type = "n";

            //adding logical i/p and o/p files for the ADAG
            if (linkType.equalsIgnoreCase("input")) {
                mVADagInputFiles.addElement(pf);
                type = "i";
            }
            else if (linkType.equalsIgnoreCase("output")) {
                mVADagOutputFiles.addElement(pf);
                type = "o";
            }
            else if (linkType.equalsIgnoreCase("inout")) {
                mVADagInputFiles.addElement(pf);
                mVADagOutputFiles.addElement(pf);
                type = "b";
            }

            //putting the filename and the
            //type information in the map
            mDagInfo.lfnMap.put(fileName,type);
            */
        }
        else if (mArgumentTag) {
            //means that the filename tag is nested in
            //an argument tag. Since dax 1.6
            //no linkage information comes
            //in this.
            mWholeCommandString = mAdjFName?
                                  //as per the default behaviour adding
                                  //a whitespace between two adjacent
                                  //filename tags
                                  mWholeCommandString  + " " + fileName:
                                  //else doing a simple concatenation
                                  mWholeCommandString  + fileName;

            mAdjFName     = true;
        }
        //dealing with profile tags
        else if (mProfileTag) { //means that filename tag is nested in a profile tag
            fileName = attrs.getValue("", "file");

            //an extra check
            if (mNamespace.equalsIgnoreCase("env")) {
                mEnvNS.checkKeyInNS(mProfileKey,fileName);
            }
        }

    } //end of dealing with fileName tags in argument tag

    /**
     * Invoked when the starting of the uses element is got. Uses tag is used to
     * denote all the files a particular job uses, be it as input , output or
     * silent.
     */
    private void handleUsesTagStart(String local, Attributes attrs) {
        String fileName = attrs.getValue("", "file");
        String linkType = attrs.getValue("", "link");
        String isTemp   = attrs.getValue("", "isTemporary");
        String type     = attrs.getValue("", "type");
        //since dax 1.6, the isTemporary
        //is broken into two transient
        //attributes dontTransfer and dontRegister
        boolean dontRegister = new Boolean(attrs.getValue("","dontRegister")).booleanValue();

        //notion of optional file since dax 1.8
        boolean optionalFile = new Boolean(attrs.getValue("","optional")).booleanValue();

        //value of dontTransfer is tri state (true,false,optional) since dax 1.7
        String dontTransfer = attrs.getValue("","dontTransfer");
        PegasusFile pf = new PegasusFile(fileName);

        //handling the transient file feature
        if (isTemp != null) {
            //this for dax 1.5 handling
            boolean temp = new Boolean(isTemp.trim()).booleanValue();
            if (temp) {
                //set the transient flags
                pf.setTransferFlag(PegasusFile.TRANSFER_NOT);
                dontRegister = true;
            }
        }
        else{
            //set the transfer mode for the file
            //for dax 1.5 onwards
            pf.setTransferFlag(dontTransfer);
        }
        //handling the dR flag
        if(dontRegister)
            pf.setTransientRegFlag();

        //handling the optional attribute
        if(optionalFile)
            pf.setFileOptional();

        //handle type of file
        if( type != null )
            pf.setType( type );

        //adding the file to input vector or the output vector
        if (linkType.trim().equalsIgnoreCase("input")) {
            mVJobInpFiles.add(pf);
        }
        else if (linkType.trim().equalsIgnoreCase("output")) {
            mVJobOutFiles.add(pf);
            //the notion of an optional file as an output would mean it
            //has the optional transfer flag set.
            if(pf.fileOptional() &&
               pf.getTransferFlag() == PegasusFile.TRANSFER_MANDATORY){
                //update the transfer flag to optional
                pf.setTransferFlag(PegasusFile.TRANSFER_OPTIONAL);
            }
        }
        else if (linkType.trim().equalsIgnoreCase("inout")) {
            mVJobInpFiles.add(pf);
            mVJobOutFiles.add(pf);
        }

    }

    /**
     * Invoked when the starting of the job element is got. The following
     * information is got from the tag
     *
     * name      : name of the job, which is the logical name of the job.
     * namespace : the namespace with which the transformation corresponding to
     *             the job is associated.
     * version   : the version of the transformation.
     */
    private void handleJobTagStart(String local, Attributes attrs) {
        String jobId = new String();
        String jobName = attrs.getValue("", "name");

        mCurrentJobSubInfo = new SubInfo();
        mCurrentJobSubInfo.condorUniverse = "vanilla";
        mCurrentJobSubInfo.namespace   = attrs.getValue("", "namespace");
        mCurrentJobSubInfo.version     = attrs.getValue("", "version");
        mCurrentJobSubInfo.dvName      = attrs.getValue("", "dv-name");
        mCurrentJobSubInfo.dvNamespace = attrs.getValue("","dv-namespace");
        mCurrentJobSubInfo.dvVersion   = attrs.getValue("","dv-version");
        mCurrentJobSubInfo.level       = (attrs.getValue("","level") == null) ?
                                         -1:
                                         Integer.parseInt(attrs.getValue("","level"));
        mCurrentJobSubInfo.logicalName = jobName;


        infoAboutAllFilesRecv = true;

        jobId = attrs.getValue("", "id");
        mCurrentJobSubInfo.logicalId = jobId;
        //mvJobIds.addElement(jobId);

        //concatenating jobname and id into job name
        jobName = jobName.concat("_");
        jobName = jobName.concat(jobId);

        mCurrentJobSubInfo.jobName = jobName;
        //mvJobsInADag.addElement(jobName);

    }

    /**
     * Invoked when the end of the job tag is reached.
     */
    private void handleJobTagEnd() {
        //adding the information about the job to mCurrentJobSubInfo
        mCurrentJobSubInfo.setInputFiles(  mVJobInpFiles );
        mCurrentJobSubInfo.setOutputFiles( mVJobOutFiles );

        //update the namespaces information
        //that is gotten through the profiles.
        mCurrentJobSubInfo.envVariables = mEnvNS;
        mCurrentJobSubInfo.hints        = mHintNS;
        mCurrentJobSubInfo.condorVariables = mCondorNS;
        mCurrentJobSubInfo.dagmanVariables = mDagmanNS;
        mCurrentJobSubInfo.vdsNS        = mVdsNS;
        mCurrentJobSubInfo.globusRSL = mGlobusNS; // shallow copy!

        //The job id for the compute jobs
        //is the name of the job itself.
        //All the jobs in the DAX are
        //compute jobs
        mCurrentJobSubInfo.jobClass = SubInfo.COMPUTE_JOB;
        mCurrentJobSubInfo.jobID = mCurrentJobSubInfo.jobName;

        //send the job to the appropriate callback implementing class
        mCallback.cbJob(mCurrentJobSubInfo);

        //reset the variables
        mCondorNS = new Condor();
        mGlobusNS = new Globus();
        mVdsNS    = new VDS();
        mEnvNS    = new ENV();
        mHintNS   = new Hints();
        mDagmanNS = new Dagman();

        mVJobInpFiles = new HashSet();
        mVJobOutFiles = new HashSet();

    }

    /**
     * Invoked when the starting of the profile element is got.
     */
    private void handleProfileTagStart(String local, Attributes attrs) {
        mProfileKey = attrs.getValue("key");
        mNamespace = attrs.getValue("namespace");
        mProfileTag = true;
    }





    /**
     * Invoked when the end of the profile element is got.
     *
     * Here we handle all the namespaces supported by Chimera at present.
     */
    private void handleProfileTagEnd() {
        mProfileTag = false;

        //setting the command line option only if textContent > 0
        if (mTextContent.length() > 0) {

            //check if namespace is valid
            mNamespace = mNamespace.toLowerCase();
            if(!Namespace.isNamespaceValid(mNamespace)){
                //reset buffer
                mTextContent.setLength( 0 );
                mLogger.log("Namespace specified in the DAX not supported. ignoring "+ mNamespace,
                            LogManager.WARNING_MESSAGE_LEVEL);
                return;
            }

            switch(mNamespace.charAt(0)){

                case 'c'://condor
                    mCondorNS.checkKeyInNS(mProfileKey,mTextContent.toString().trim());
                    break;

                case 'd'://dagman
                    mDagmanNS.checkKeyInNS(mProfileKey,mTextContent.toString().trim());
                    break;

                case 'e'://env
                    mEnvNS.checkKeyInNS(mProfileKey,mTextContent.toString().trim());
                    break;

                case 'g'://globus
                    mGlobusNS.checkKeyInNS(mProfileKey,mTextContent.toString().trim());
                    break;

                case 'h'://hint
                    mHintNS.checkKeyInNS(mProfileKey,mTextContent.toString().trim());
                    break;

                case 'v'://vds
                    mVdsNS.checkKeyInNS(mProfileKey,mTextContent.toString().trim());
                    break;

                default:
                    //ignore should not come here ever.
                    mLogger.log("Namespace not supported. ignoring "+ mNamespace,
                            LogManager.WARNING_MESSAGE_LEVEL);
                    break;

            }


            //resetting the buffer
            mTextContent.setLength(0);
            mProfileKey = "";
            mNamespace = "";

        }
    }

    /**
     * Invoked when the starting of the child element is got. The child element
     * gives us the child of an edge of the dag. The edge being parent->child.
     */
    private void handleChildTagStart(String local, Attributes attrs) {
        mCurrentChildId = "";
        mCurrentChildId = attrs.getValue("", "ref");
        mParents        = new LinkedList();
    }

    /**
     * This passes the child and it's parents list to the callback object.
     */
    private void handleChildTagEnd(){
        //String childName = lookupName(mCurrentChildId);
        mCallback.cbParents(mCurrentChildId,mParents);
    }

    /**
     * Invoked when the starting of the parent element is got. The child element
     * gives us the child of an edge of the dag. The edge being parent->child.
     */
    private void handleParentTagStart(String local, Attributes attrs) {
        //stores the child parent Relation
        PCRelation parentChildRelation = new PCRelation();

        String childName = new String();
        String parentName = new String();
        String parentId = attrs.getValue("", "ref");

        //looking up the parent name
        //parentName = lookupName(parentId);
        mParents.add(parentId);
    }

    /**
     * Invoked when the starting of the Argument Tag is reached. Just set a
     * boolean variable
     */
    private void handleArgumentTagStart(String local, Attributes attrs) {
        //setting the boolean variable.
        mArgumentTag = true;
        //set the adjacency flag for
        //adjacent filename to false
        mAdjFName     = false;
    }

    /**
     * Invoked when the end of the Argument Tag is reached.
     *
     * The buffers are reset
     */
    private void handleArgumentTagEnd() {
        mArgumentTag = false;
        mWholeCommandString = mWholeCommandString.concat(new String(
            mTextContent));

        mWholeCommandString = this.ignoreWhitespace(mWholeCommandString);
        //adding the commmand string
        mCurrentJobSubInfo.strargs = new String(mWholeCommandString);

        //resetting mWholeCommandString
        mWholeCommandString = "";

        //resetting the buffer
        mTextContent.setLength(0);

    }

    /**
     * Invoked when the starting of the stdout tag is reached.
     * Used to specify the stdout of the application by the user. It can be
     * a file also.
     */
    private void handleStdoutTagStart(String local, Attributes attrs) {
        mCurrentJobSubInfo.stdOut = attrs.getValue("", "file");
    }

    /**
     * Invoked when the starting of the stdin tag is reached.
     * Used to specify the stdout of the application by the user. It can be
     * a file also.
     */
    private void handleStdinTagStart(String local, Attributes attrs) {
        mCurrentJobSubInfo.stdIn = attrs.getValue("", "file");
    }

    /**
     * Invoked when the starting of the stdout tag is reached.
     * Used to specify the stderr of the application by the user. It can be
     * a file also.
     */
    private void handleStderrTagStart(String local, Attributes attrs) {
        mCurrentJobSubInfo.stdErr = attrs.getValue("", "file");
    }


    /**
     * Overrides the default implementation when the elements end tag comes.
     * This method is called automatically by the Sax parser when the end tag of
     * an element comes in the xml file.
     */

    public void endElement(String uri, String localName, String qName) {
        /*System.out.println("element end tag ---------");
                 System.out.println("line number "+ locator.getLineNumber());
                 System.out.println("URI: "+ uri);
                 System.out.println("local name " + localName);
                 System.out.println("qname: "+qName);*/

        boolean temp = true;
        String universe = "vanilla"; //by default jobs are vanilla

        //when we get the end tag of argument, we change reset the currentCommOpt
        if (localName.equals("argument")) { // || localName.trim().equalsIgnoreCase("job")){
            handleArgumentTagEnd();
        }
        else if (localName.equals("job")) {
            handleJobTagEnd();
        }
        else if (localName.equals("profile")) {
            handleProfileTagEnd();
        }
        else if (localName.equals("child")) {
            handleChildTagEnd();
        }
        else if(localName.equals("adag")){
            //call the callback interface
            mCallback.cbDone();
            return;
        }
   }

    /**
     *  Here we have all the elements in our data structure. This is called
     *  automatically when the end of the XML file is reached.
     */
    public void endDocument() {

    }

    /**
     * The main program. The DaxParser can be run standalone, by which it just
     * parses the file and populates the required data objects.
     *
     */

    public static void main(String args[]) {
        //System.setProperty("vds.home","/nfs/asd2/vahi/test/chimera/");
        //DaxParser d = new DaxParser("sdss.xml","isi",null);
        //DaxParser d = new DaxParser("sonal.xml",new DAX2CDAG("./sonal.xml"));
        //DaxParser d = new DaxParser("./testcases/black-diamond/blackdiamond_dax_1.7.xml");
        //DaxParser d = new DaxParser("/nfs/asd2/vahi/gurmeet_dax.xml");

        /*DagInfo dagInfo = d.getDagInfo();

        Vector vSubInfo = d.getSubInfo();

        ADag adag = new ADag(dagInfo, vSubInfo);

        System.out.println(adag);
        */

    }


    /**
     * Helps the load database to locate the DAX XML schema, if available.
     * Please note that the schema location URL in the instance document
     * is only a hint, and may be overriden by the findings of this method.
     *
     * @return a location pointing to a definition document of the XML
     * schema that can read VDLx. Result may be null, if such a document
     * is unknown or unspecified.
     */
    public String getSchemaLocation() {
        // treat URI as File, yes, I know - I need the basename
        File uri = new File(DaxParser.SCHEMA_LOCATION);

        //get the default version with decimal point shifted right
        float defaultVersion = shiftRight( extractVersionFromSchema( uri.getName() ) );

        float schemaVersion = shiftRight( mDaxSchemaVersion );


        String child = ( schemaVersion == -1 || schemaVersion > defaultVersion)?
                       //use the default
                       uri.getName():
                       //use the schema version specified in the dax
                       "dax-" + mDaxSchemaVersion + ".xsd";

        // create a pointer to the default local position
        File dax = new File(this.mProps.getSysConfDir(), child);

        //System.out.println("\nDefault Location of Dax is " + dax.getAbsolutePath());

        // Nota bene: vds.schema.dax may be a networked URI...
        return this.mProps.getDAXSchemaLocation(dax.getAbsolutePath());
    }

    /**
     * Returns a float with the decimal point shifted right till the end.
     * Is necessary for comparing a String "1.10" with a String "1.9".
     *
     * @param value  the value that has to be shifted right.
     *
     * @return the float value, with the decimal point shifted or -1 in case
     *         of error.
     */
    public float shiftRight(String value){
        float result = -1;

        //sanity check in case of null value
        if( value == null ) return result;

        value = value.trim();
        int i = 0;
        for( i = 0; i < value.length(); i++){
            char c = value.charAt(i);

            //parse till the first '.'
            if ( c >= '0' && c <= '9' ) {
                continue;
            }
            else if ( c == '.' ) {
                i++;
                break;
            }
            else{
                //invalid string
                return result;
            }
        }

        //determine the multiplicative factor
        int factor = 1;
        for ( i = i ; i < value.length(); i++, factor *= 10 ){
            char c = value.charAt(i);

            //exit if any of the trailing characters are non digits
            if ( ! ( c >= '0' && c <= '9') ) return result;
        }

        result = Float.parseFloat(value) * factor;

        return result;
    }

}
