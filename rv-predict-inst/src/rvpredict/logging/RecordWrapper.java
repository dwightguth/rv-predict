/*******************************************************************************
 * Copyright (c) 2013 University of Illinois
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package rvpredict.logging;

import java.util.HashMap;
import java.util.Properties;

import soot.Body;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.LongType;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.ShortType;
import soot.SootClass;
import soot.SootField;
import soot.SootMethodRef;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.Local;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.EnterMonitorStmt;
import soot.jimple.ExitMonitorStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;

/**
 * The RecordWrapper class includes utilities for instrumenting the classes
 * for the record version.
 * @author jeffhuang
 *
 */
public final class RecordWrapper {
	
	private static final String RV_LOCAL = "rv_local";
	private static HashMap<String,Integer> methodRVLocalMap = new HashMap<String,Integer>();

	
  private static final SootClass logClass = Scene.v().loadClassAndSupport("rvpredict.logging.RecordRT");
  private static final SootMethodRef logLockMethod = logClass.getMethod("void logLock(int,java.lang.Object)").makeRef();
  private static final SootMethodRef logUnlockMethod = logClass.getMethod("void logUnlock(int,java.lang.Object)").makeRef();
  private static final SootMethodRef logWaitMethod = logClass.getMethod("void logWait(int,java.lang.Object)").makeRef();
  private static final SootMethodRef logNotifyMethod = logClass.getMethod("void logNotify(int,java.lang.Object)").makeRef();
  private static final SootMethodRef logFieldAccMethod = logClass.getMethod("void logFieldAcc(int,java.lang.Object,int,java.lang.Object,boolean)").makeRef();
  private static final SootMethodRef logArrayAccMethod = logClass.getMethod("void logArrayAcc(int,java.lang.Object,int,java.lang.Object,boolean)").makeRef();
  private static final SootMethodRef logInitialWriteAccMethod = logClass.getMethod("void logInitialWrite(int,java.lang.Object,int,java.lang.Object)").makeRef();
  private static final SootMethodRef logStartMethod = logClass.getMethod("void logStart(int,java.lang.Object)").makeRef();
  private static final SootMethodRef logJoinMethod = logClass.getMethod("void logJoin(int,java.lang.Object)").makeRef();
  private static final SootMethodRef logBranchMethod = logClass.getMethod("void logBranch(int)").makeRef();
  private static final SootMethodRef logBBMethod = logClass.getMethod("void logBasicBlock(int)").makeRef();
  private static final SootMethodRef logStaticSyncLockMethod = logClass.getMethod("void logStaticSyncLock(int,int)").makeRef();
  private static final SootMethodRef logStaticSyncUnlockMethod = logClass.getMethod("void logStaticSyncUnlock(int,int)").makeRef();

  private static final SootMethodRef valueOfBooleanMethod = Scene.v().getMethod("<java.lang.Boolean: java.lang.Boolean valueOf(boolean)>").makeRef();
  private static final SootMethodRef valueOfByteMethod = Scene.v().getMethod("<java.lang.Byte: java.lang.Byte valueOf(byte)>").makeRef();
  private static final SootMethodRef valueOfCharMethod = Scene.v().getMethod("<java.lang.Character: java.lang.Character valueOf(char)>").makeRef();
  private static final SootMethodRef valueOfDoubleMethod = Scene.v().getMethod("<java.lang.Double: java.lang.Double valueOf(double)>").makeRef();
  private static final SootMethodRef valueOfFloatMethod = Scene.v().getMethod("<java.lang.Float: java.lang.Float valueOf(float)>").makeRef();
  private static final SootMethodRef valueOfIntMethod = Scene.v().getMethod("<java.lang.Integer: java.lang.Integer valueOf(int)>").makeRef();
  private static final SootMethodRef valueOfLongMethod = Scene.v().getMethod("<java.lang.Long: java.lang.Long valueOf(long)>").makeRef();
  private static final SootMethodRef valueOfShortMethod = Scene.v().getMethod("<java.lang.Short: java.lang.Short valueOf(short)>").makeRef();


  private static String  getRVLocalName(String methodSig)
  {
	  Integer id = methodRVLocalMap.get(methodSig);
	  if(id==null)
	  {
		  id=0;
	  }
	  methodRVLocalMap.put(methodSig, ++id);
	  
	  return RV_LOCAL+id;
  }

  
  public static InvokeStmt logBranch(int id, final Stmt s) {
    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logBranchMethod,
        IntConstant.v(id));
    return Jimple.v().newInvokeStmt(logExpr);
  }
  public static InvokeStmt logBasicBlock(int id, final Stmt s) {
	    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logBBMethod,
	        IntConstant.v(id));
	    return Jimple.v().newInvokeStmt(logExpr);
	  }

//  public static InvokeStmt logLock(SootClass c) {
//    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logStaticSyncLockMethod,
//        StringConstant.v(c.getName()));
//    return Jimple.v().newInvokeStmt(logExpr);
//  }

  public static InvokeStmt logLock(int id, Stmt s) {
        return logLock(id, ((EnterMonitorStmt)s).getOp());
  }
  public static InvokeStmt logLock(int id, int sid) {
	    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logStaticSyncLockMethod,
	    		IntConstant.v(id),
	    		IntConstant.v(sid));
	    return Jimple.v().newInvokeStmt(logExpr);
  }	
  public static InvokeStmt logLock(int id, Value v) {
    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logLockMethod,
    		IntConstant.v(id),
        v);
    return Jimple.v().newInvokeStmt(logExpr);
  }

  public static InvokeStmt logWait(int id, Stmt s){
	    InstanceInvokeExpr e = (InstanceInvokeExpr)s.getInvokeExpr();

	    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logWaitMethod,
	    	IntConstant.v(id),	
	        e.getBase());
	    return Jimple.v().newInvokeStmt(logExpr);
	  }

  public static InvokeStmt logNotify(int id, Stmt s){
    InstanceInvokeExpr e = (InstanceInvokeExpr)s.getInvokeExpr();

    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logNotifyMethod,
    		IntConstant.v(id),	
        e.getBase());
    return Jimple.v().newInvokeStmt(logExpr);
  }

//  public static InvokeStmt logUnlock(SootClass c) {
//    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logStaticSyncUnlockMethod,
//        StringConstant.v(c.getName()));
//    return Jimple.v().newInvokeStmt(logExpr);
//  }

  public static InvokeStmt logUnlock(int id, Stmt s) {
    return logUnlock(id, ((ExitMonitorStmt)s).getOp());
  }
  public static InvokeStmt logUnlock(int id, int sid) {
	  InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logStaticSyncUnlockMethod,
	    		IntConstant.v(id),
	    		IntConstant.v(sid));
	    return Jimple.v().newInvokeStmt(logExpr);
	  }
  public static InvokeStmt logUnlock(int id, Value v) {
    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logUnlockMethod,
    		IntConstant.v(id),
        v);
    return Jimple.v().newInvokeStmt(logExpr);
  }

  
  public static void logArrayAcc(int id, Body body, Stmt s)
  {
	  Local rv_local1,rv_local2,rv_local3;
	  AssignStmt newAssignStmt1,newAssignStmt2,newAssignStmt3;

	  Value base = s.getArrayRef().getBase();
	  Value index = s.getArrayRef().getIndex();
	  
	  DefinitionStmt d = (DefinitionStmt)s;
	    boolean write = (d.getLeftOp() instanceof ArrayRef);
	    
		
	    Value v;
	    if(write)
	    	v = d.getRightOp();
	    else
	    	v = d.getLeftOp();
	    
    	String rv_local1_name = getRVLocalName(body.getMethod().getSignature());
    	String rv_local2_name = getRVLocalName(body.getMethod().getSignature());
    	String rv_local3_name = getRVLocalName(body.getMethod().getSignature());


		rv_local1 = Jimple.v().newLocal(rv_local1_name, base.getType()); 
		rv_local2 = Jimple.v().newLocal(rv_local2_name, index.getType()); 
		
        newAssignStmt1 = Jimple.v().newAssignStmt(rv_local1, base);
        newAssignStmt2 = Jimple.v().newAssignStmt(rv_local2, index);
		
	    if(v.getType() instanceof PrimType)
	    {
	    	Value staticInvoke;

	    	if(v.getType() instanceof BooleanType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Boolean"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfBooleanMethod,v);  
	    	}
	    	else if(v.getType() instanceof ByteType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Byte"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfByteMethod,v); 
	    	}
	    	else if(v.getType() instanceof CharType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Char"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfCharMethod,v); 
	    	}
	    	else if(v.getType() instanceof DoubleType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Double"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfDoubleMethod,v); 
	    	}
	    	else if(v.getType() instanceof FloatType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Float"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfFloatMethod,v); 
	    	}
	    	else if(v.getType() instanceof IntType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Int"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfIntMethod,v); 
	    	}
	    	else if(v.getType() instanceof LongType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Long"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfLongMethod,v); 
	    	}
	    	else//if (v.getType() instanceof ShortType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Short"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfShortMethod,v); 
	    	}
			
	    	newAssignStmt3 = Jimple.v().newAssignStmt(rv_local3, staticInvoke);
			 			 
	    }
	    else
	    {
			rv_local3 = Jimple.v().newLocal(rv_local3_name, v.getType()); 
	        newAssignStmt3 = Jimple.v().newAssignStmt(rv_local3, v);

	    }
		
		


        body.getLocals().add(rv_local1);
        body.getLocals().add(rv_local2);
        body.getLocals().add(rv_local3);

    
    //body.getUnits().insertAfter(newAssignStmt1,s);
    //body.getUnits().insertAfter(newAssignStmt2,newAssignStmt1);
    body.getUnits().insertAfter(newAssignStmt2,s);
    body.getUnits().insertAfter(newAssignStmt3,newAssignStmt2);

    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logArrayAccMethod,
    		IntConstant.v(id),
    		//rv_local1,
    		base,
    		rv_local2,
    		rv_local3,
        IntConstant.v(write?1:0));
    
    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(logExpr);
	 body.getUnits().insertAfter(invokeStmt,newAssignStmt3);
    
    //return invokeStmt;
  }
  public static void logInitialArrayWriteAcc(int id, Body body, Stmt s)
  {
	  Local rv_local1,rv_local2,rv_local3;
	  AssignStmt newAssignStmt1,newAssignStmt2,newAssignStmt3;

	  Value base = s.getArrayRef().getBase();
	  Value index = s.getArrayRef().getIndex();
	  
	  DefinitionStmt d = (DefinitionStmt)s;
	    boolean write = (d.getLeftOp() instanceof ArrayRef);
	    
		
	    Value v;
	    if(write)
	    	v = d.getRightOp();
	    else
	    	v = d.getLeftOp();
	    
    	String rv_local1_name = getRVLocalName(body.getMethod().getSignature());
    	String rv_local2_name = getRVLocalName(body.getMethod().getSignature());
    	String rv_local3_name = getRVLocalName(body.getMethod().getSignature());


		rv_local1 = Jimple.v().newLocal(rv_local1_name, base.getType()); 
		rv_local2 = Jimple.v().newLocal(rv_local2_name, index.getType()); 
		
        newAssignStmt1 = Jimple.v().newAssignStmt(rv_local1, base);
        newAssignStmt2 = Jimple.v().newAssignStmt(rv_local2, index);
		
	    if(v.getType() instanceof PrimType)
	    {
	    	Value staticInvoke;

	    	if(v.getType() instanceof BooleanType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Boolean"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfBooleanMethod,v);  
	    	}
	    	else if(v.getType() instanceof ByteType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Byte"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfByteMethod,v); 
	    	}
	    	else if(v.getType() instanceof CharType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Char"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfCharMethod,v); 
	    	}
	    	else if(v.getType() instanceof DoubleType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Double"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfDoubleMethod,v); 
	    	}
	    	else if(v.getType() instanceof FloatType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Float"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfFloatMethod,v); 
	    	}
	    	else if(v.getType() instanceof IntType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Int"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfIntMethod,v); 
	    	}
	    	else if(v.getType() instanceof LongType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Long"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfLongMethod,v); 
	    	}
	    	else//if (v.getType() instanceof ShortType)
	    	{
	    		rv_local3 = Jimple.v().newLocal(rv_local3_name, RefType.v("java.lang.Short"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfShortMethod,v); 
	    	}
			
	    	newAssignStmt3 = Jimple.v().newAssignStmt(rv_local3, staticInvoke);
			 			 
	    }
	    else
	    {
			rv_local3 = Jimple.v().newLocal(rv_local3_name, v.getType()); 
	        newAssignStmt3 = Jimple.v().newAssignStmt(rv_local3, v);

	    }
		
		
        body.getLocals().add(rv_local1);
        body.getLocals().add(rv_local2);
        body.getLocals().add(rv_local3);

    
    //body.getUnits().insertAfter(newAssignStmt1,s);
    //body.getUnits().insertAfter(newAssignStmt2,newAssignStmt1);
        
        Unit insertPoint = s;
        Unit nextPoint =  body.getUnits().getSuccOf(s);
        //starting from nextPoint, find the super call until the end
        String superClassName = body.getMethod().getDeclaringClass().getSuperclass().getName();
        while(nextPoint!=null)//super call
        {
        	if(nextPoint.toString().contains("specialinvoke r0.<"+superClassName+": void <init>"))
        	{
                insertPoint = nextPoint;
                break;
        	}
        	
        	nextPoint =  body.getUnits().getSuccOf(nextPoint);
        }
        
    body.getUnits().insertAfter(newAssignStmt2,insertPoint);
    body.getUnits().insertAfter(newAssignStmt3,newAssignStmt2);

    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logInitialWriteAccMethod,
    		IntConstant.v(id),
    		//rv_local1,
    		base,
    		rv_local2,
    		rv_local3);
    
    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(logExpr);
	 body.getUnits().insertAfter(invokeStmt,newAssignStmt3);
  }
  public static void logInitialFieldWriteAcc(int id, int sid, Body body, Stmt s) {
	  
	  Value instanceObject;
	    if (s.getFieldRef() instanceof InstanceFieldRef) {
	      instanceObject = ((InstanceFieldRef)s.getFieldRef()).getBase();
	    } else {
	      instanceObject = NullConstant.v();
	    }
	    
		Local rv_local;
		AssignStmt newAssignStmt;
		
	    Value v= ((DefinitionStmt)s).getRightOp();

	        
		String rv_local_name = getRVLocalName(body.getMethod().getSignature());

	    if(v.getType() instanceof PrimType)
	    {
	    	Value staticInvoke;

	    	if(v.getType() instanceof BooleanType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Boolean"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfBooleanMethod,v);  
	    	}
	    	else if(v.getType() instanceof ByteType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Byte"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfByteMethod,v); 
	    	}
	    	else if(v.getType() instanceof CharType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Char"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfCharMethod,v); 
	    	}
	    	else if(v.getType() instanceof DoubleType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Double"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfDoubleMethod,v); 
	    	}
	    	else if(v.getType() instanceof FloatType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Float"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfFloatMethod,v); 
	    	}
	    	else if(v.getType() instanceof IntType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Int"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfIntMethod,v); 
	    	}
	    	else if(v.getType() instanceof LongType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Long"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfLongMethod,v); 
	    	}
	    	else//if (v.getType() instanceof ShortType)
	    	{
	    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Short"));           
	            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfShortMethod,v); 
	    	}
			
	        newAssignStmt = Jimple.v().newAssignStmt(rv_local, staticInvoke);
			 			 
	    }
	    else
	    {
			rv_local = Jimple.v().newLocal(rv_local_name, v.getType());           
	        newAssignStmt = Jimple.v().newAssignStmt(rv_local, v);

	    }

	    body.getLocals().add(rv_local);
	    
        Unit insertPoint = s;
        Unit nextPoint =  body.getUnits().getSuccOf(insertPoint);
        //starting from nextPoint, find the super call until the end
        String superClassName = body.getMethod().getDeclaringClass().getSuperclass().getName();
        while(nextPoint!=null)//super call
        {
        	if(nextPoint.toString().contains("specialinvoke r0.<"+superClassName+": void <init>"))
        	{
                insertPoint = nextPoint;
                break;
        	}
        	
        	nextPoint =  body.getUnits().getSuccOf(nextPoint);
        }
	    
	    body.getUnits().insertAfter(newAssignStmt,insertPoint);
	    
	    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logInitialWriteAccMethod,
	    		IntConstant.v(id),
	    		instanceObject,
	    		IntConstant.v(sid),
	    		rv_local);
	    
	    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(logExpr);
		 body.getUnits().insertAfter(invokeStmt,newAssignStmt);
	    
  }
  public static void logFieldAcc(int id, int sid, Body body, Stmt s) {
    DefinitionStmt d = (DefinitionStmt)s;
    boolean write = (d.getLeftOp() instanceof FieldRef);
    Value instanceObject;
    if (s.getFieldRef() instanceof InstanceFieldRef) {
      instanceObject = ((InstanceFieldRef)s.getFieldRef()).getBase();
    } else {
      instanceObject = NullConstant.v();
    }
    
	Local rv_local;
	AssignStmt newAssignStmt;
	
    Value v;
    if(write)
    	v = d.getRightOp();
    else
    	v = d.getLeftOp();
        
	String rv_local_name = getRVLocalName(body.getMethod().getSignature());

    if(v.getType() instanceof PrimType)
    {
    	Value staticInvoke;

    	if(v.getType() instanceof BooleanType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Boolean"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfBooleanMethod,v);  
    	}
    	else if(v.getType() instanceof ByteType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Byte"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfByteMethod,v); 
    	}
    	else if(v.getType() instanceof CharType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Char"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfCharMethod,v); 
    	}
    	else if(v.getType() instanceof DoubleType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Double"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfDoubleMethod,v); 
    	}
    	else if(v.getType() instanceof FloatType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Float"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfFloatMethod,v); 
    	}
    	else if(v.getType() instanceof IntType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Int"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfIntMethod,v); 
    	}
    	else if(v.getType() instanceof LongType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Long"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfLongMethod,v); 
    	}
    	else//if (v.getType() instanceof ShortType)
    	{
    		rv_local = Jimple.v().newLocal(rv_local_name, RefType.v("java.lang.Short"));           
            staticInvoke = Jimple.v().newStaticInvokeExpr(valueOfShortMethod,v); 
    	}
		
        newAssignStmt = Jimple.v().newAssignStmt(rv_local, staticInvoke);
		 			 
    }
    else
    {
		rv_local = Jimple.v().newLocal(rv_local_name, v.getType());           
        newAssignStmt = Jimple.v().newAssignStmt(rv_local, v);

    }

    body.getLocals().add(rv_local);
    body.getUnits().insertAfter(newAssignStmt,s);
    
    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logFieldAccMethod,
    		IntConstant.v(id),
    		instanceObject,
    		IntConstant.v(sid),
    		rv_local,
        IntConstant.v(write?1:0));
    
    InvokeStmt invokeStmt = Jimple.v().newInvokeStmt(logExpr);
	 body.getUnits().insertAfter(invokeStmt,newAssignStmt);

    
   // return invokeStmt;
    
  }

//  public static InvokeStmt logArrayAcc(Stmt s, SootClass c, SootMethod m) {
//    ArrayRef ar = s.getArrayRef();
//    DefinitionStmt d = (DefinitionStmt)s;
//    boolean write = (d.getLeftOp() instanceof FieldRef);
//
//    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logArrayAccMethod,
//        StringConstant.v(ar.getType().toString()),
//        ar.getBase(),
//        ar.getIndex(),
//        StringConstant.v(c.getName()),
//        StringConstant.v(m.getSubSignature()),
//        IntConstant.v(/*getJimpleLine(s)*/-1),
//        IntConstant.v(write?1:0));
//    return Jimple.v().newInvokeStmt(logExpr);
//  }

  public static InvokeStmt logStart(int id, Stmt s) {
    InstanceInvokeExpr e = (InstanceInvokeExpr) s.getInvokeExpr();

    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logStartMethod,
    		IntConstant.v(id),
        e.getBase());
    return Jimple.v().newInvokeStmt(logExpr);
  }

  public static InvokeStmt logJoin(int id, Stmt s) {
    InstanceInvokeExpr e = (InstanceInvokeExpr) s.getInvokeExpr();

    InvokeExpr logExpr = Jimple.v().newStaticInvokeExpr(logJoinMethod,
    		IntConstant.v(id),
        e.getBase());
    return Jimple.v().newInvokeStmt(logExpr);
  }

}
// vim: tw=100:sw=2