package z3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class Z3Run
{
	private static String Z3_SMT = "z3smt.";
	private static String Z3_OUT = "z3out.";
	private static String Z3_ERR = "z3err.";
	File smtFile,z3OutFile,z3ErrFile;
	
	Z3Model model;
	
	private static String CMD = "z3 -smt2 ";
	
	boolean sat;
	
	public Z3Run(int id)
	{				
		try{
		smtFile = Util.newOutFile(Z3_SMT+id);
        
		z3OutFile = Util.newOutFile(Z3_OUT+id);
        
		z3ErrFile = Util.newOutFile(Z3_ERR+id);
		}catch(IOException e)
		{
			System.err.println(e.getMessage());
		}
	}
	public void sendMessage(String msg)
	{
		PrintWriter smtWriter = null;
		try{
			smtWriter = Util.newWriter(smtFile, true);
			smtWriter.println(msg);
		    smtWriter.close();
		    
	        exec(z3OutFile, z3ErrFile, smtFile.getAbsolutePath());

	        model = Z3ModelReader.read(z3OutFile);
	        
	        //String z3OutFileName = z3OutFile.getAbsolutePath();
	        //retrieveResult(z3OutFileName);
		    
		}catch(IOException e)
		{
			System.err.println(e.getMessage());

		}
	}
	
	public void exec(File outFile, File errFile, String file) throws IOException
	{
		
		String cmds = CMD + file;

//		args2 += " 1>"+outFile;
//		args2 += " 2>"+errFile;
//
//		args2 = args2 + "\"";

		//cmds = "z3 -version";
		
		Process process = Runtime.getRuntime().exec(cmds); 
		InputStream inputStream = process.getInputStream();
		// write the inputStream to a FileOutputStream
		OutputStream out = new FileOutputStream(outFile);
	 
		int read = 0;
		byte[] bytes = new byte[1024];
	 
		while ((read = inputStream.read(bytes)) != -1) {
			out.write(bytes, 0, read);
		}
	 
		inputStream.close();
		out.flush();
		out.close();
		//setError(errFile);
		//setOutput(outFile);

	}
	
	public static void main(String[] args) throws IOException
	{
		String msg ="(declare-const a Int)\n"+
					"(declare-const b Int)\n"+
					"(declare-const c Int)\n"+
					"(declare-const d Int)\n"+
					"(assert (> a 0))\n"+
					"(assert (> c 0))\n"+
					"(assert (< a b))\n"+
					"(assert (< c d))\n"+
					"(assert (not (= a c)))\n"+
					"(assert (not (= a d)))\n"+
					"(assert (not (= b c)))\n"+
					"(assert (not (= b c)))";
		
		Z3Run task = new Z3Run(1);
		task.sendMessage(msg);
		Iterator<Entry<String,Object>> setIt = task.model.getMap().entrySet().iterator();
		while(setIt.hasNext())
		{
			Entry<String,Object> entry = setIt.next();
			System.out.println(entry.getKey()+": "+entry.getValue());
		}
	}
}
