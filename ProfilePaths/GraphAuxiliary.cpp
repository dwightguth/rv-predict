//===-- GrapAuxillary.cpp- Auxillary functions on graph ----------*- C++ -*--=//
//
//auxillary function associated with graph: they
//all operate on graph, and help in inserting
//instrumentation for trace generation
//
//===----------------------------------------------------------------------===//

#include "llvm/Transforms/Utils/UnifyFunctionExitNodes.h"
#include "llvm/Function.h"
#include "llvm/Pass.h"
#include "llvm/BasicBlock.h"
#include "llvm/InstrTypes.h"
#include "llvm/Transforms/Instrumentation/Graph.h"
#include <algorithm>
#include <iostream>
#include <sstream>
#include <string>

//using std::list;
using std::map;
using std::vector;
using std::cerr;

//check if 2 edges are equal (same endpoints and same weight)
static bool edgesEqual(Edge  ed1, Edge ed2){
  return ((ed1==ed2) && ed1.getWeight()==ed2.getWeight());
}

//Get the vector of edges that are to be instrumented in the graph
static void getChords(vector<Edge > &chords, Graph &g, Graph st){
  //make sure the spanning tree is directional
  //iterate over ALL the edges of the graph
  vector<Node *> allNodes=g.getAllNodes();
  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList node_list=g.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=node_list.begin(), NLE=node_list.end(); 
	NLI!=NLE; ++NLI){
      Edge f(*NI, NLI->element,NLI->weight, NLI->randId);
      if(!(st.hasEdgeAndWt(f)))//addnl
	chords.push_back(f);
    }
  }
}

//Given a tree t, and a "directed graph" g
//replace the edges in the tree t with edges that exist in graph
//The tree is formed from "undirectional" copy of graph
//So whatever edges the tree has, the undirectional graph 
//would have too. This function corrects some of the directions in 
//the tree so that now, all edge directions in the tree match
//the edge directions of corresponding edges in the directed graph
static void removeTreeEdges(Graph &g, Graph& t){
  vector<Node* > allNodes=t.getAllNodes();
  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList nl=t.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=nl.begin(), NLE=nl.end();	NLI!=NLE;++NLI){
      Edge ed(NLI->element, *NI, NLI->weight);
      if(!g.hasEdgeAndWt(ed)) t.removeEdge(ed);//tree has only one edge
      //between any pair of vertices, so no need to delete by edge wt
    }
  }
}

//Assign a value to all the edges in the graph
//such that if we traverse along any path from root to exit, and
//add up the edge values, we get a path number that uniquely
//refers to the path we travelled
int valueAssignmentToEdges(Graph& g){
  vector<Node *> revtop=g.reverseTopologicalSort();
  map<Node *,int > NumPaths;
  for(vector<Node *>::iterator RI=revtop.begin(), RE=revtop.end(); 
      RI!=RE; ++RI){
    if(g.isLeaf(*RI))
      NumPaths[*RI]=1;
    else{
      NumPaths[*RI]=0;

      Graph::nodeList &nlist=g.getNodeList(*RI);
      //sort nodelist by increasing order of numpaths
      
      int sz=nlist.size();
      
      //printing BB list
      //std::cerr<<"node list------------\n";
      //for(Graph::nodeList::iterator NLI=nlist.begin(), NLE=nlist.end(); 
      //  NLI!=NLE; ++NLI)
      //std::cerr<<NLI->element->getElement()->getName()<<"->";
      
      //std::cerr<<"\n-----------\n";

      for(int i=0;i<sz-1; i++){
	int min=i;
	for(int j=i+1; j<sz; j++){
          BasicBlock *bb1 = nlist[j].element->getElement();
          BasicBlock *bb2 = nlist[min].element->getElement();
          assert(bb1->getParent() == bb2->getParent() && 
                 "BBs with diff parents"); 
          TerminatorInst *ti = bb1->getTerminator();

          //compare the order of BBs in the terminator instruction
          for(int x=0, y = ti->getNumSuccessors(); x < y; x++){
            if(ti->getSuccessor(x) == bb1){ //bb1 occurs first
              min = j;
              break;
            }
            if(ti->getSuccessor(x) == bb2) //bb2 occurs first
              break;
          }
          
        }
	graphListElement tempEl=nlist[min];
	nlist[min]=nlist[i];
	nlist[i]=tempEl;
      }
      
      //sorted now!
      for(Graph::nodeList::iterator GLI=nlist.begin(), GLE=nlist.end();
	  GLI!=GLE; ++GLI){
	GLI->weight=NumPaths[*RI];
	NumPaths[*RI]+=NumPaths[GLI->element];
      }
    }
  }
  return NumPaths[g.getRoot()];
}

//This is a helper function to get the edge increments
//This is used in conjuntion with inc_DFS
//to get the edge increments
//Edge increment implies assigning a value to all the edges in the graph
//such that if we traverse along any path from root to exit, and
//add up the edge values, we get a path number that uniquely
//refers to the path we travelled
//inc_Dir tells whether 2 edges are in same, or in different directions
//if same direction, return 1, else -1
static int inc_Dir(Edge e, Edge f){ 
 if(e.isNull()) 
    return 1;
 
 //check that the edges must have atleast one common endpoint
  assert(*(e.getFirst())==*(f.getFirst()) ||
	 *(e.getFirst())==*(f.getSecond()) || 
	 *(e.getSecond())==*(f.getFirst()) ||
	 *(e.getSecond())==*(f.getSecond()));

  if(*(e.getFirst())==*(f.getSecond()) || 
     *(e.getSecond())==*(f.getFirst()))
    return 1;
  
  return -1;
}


//used for getting edge increments (read comments above in inc_Dir)
//inc_DFS is a modification of DFS 
static void inc_DFS(Graph& g,Graph& t,map<Edge, int, EdgeCompare>& Increment, 
	     int events, Node *v, Edge e){
  
  vector<Node *> allNodes=t.getAllNodes();

  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList node_list=t.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=node_list.begin(), NLE=node_list.end(); 
	NLI!= NLE; ++NLI){
      Edge f(*NI, NLI->element,NLI->weight, NLI->randId);
      if(!edgesEqual(f,e) && *v==*(f.getSecond())){
	int dir_count=inc_Dir(e,f);
	int wt=1*f.getWeight();
	inc_DFS(g,t, Increment, dir_count*events+wt, f.getFirst(), f);
      }
    }
  }

  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList node_list=t.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=node_list.begin(), NLE=node_list.end(); 
	NLI!=NLE; ++NLI){
      Edge f(*NI, NLI->element,NLI->weight, NLI->randId);
      if(!edgesEqual(f,e) && *v==*(f.getFirst())){
      	int dir_count=inc_Dir(e,f);
	int wt=f.getWeight();
	inc_DFS(g,t, Increment, dir_count*events+wt, 
		f.getSecond(), f);
      }
    }
  }

  allNodes=g.getAllNodes();
  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList node_list=g.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=node_list.begin(), NLE=node_list.end(); 
	NLI!=NLE; ++NLI){
      Edge f(*NI, NLI->element,NLI->weight, NLI->randId);
      if(!(t.hasEdgeAndWt(f)) && (*v==*(f.getSecond()) || 
				  *v==*(f.getFirst()))){
	int dir_count=inc_Dir(e,f);
	Increment[f]+=dir_count*events;
      }
    }
  }
}

//Now we select a subset of all edges
//and assign them some values such that 
//if we consider just this subset, it still represents
//the path sum along any path in the graph
static map<Edge, int, EdgeCompare> getEdgeIncrements(Graph& g, Graph& t){
  //get all edges in g-t
  map<Edge, int, EdgeCompare> Increment;

  vector<Node *> allNodes=g.getAllNodes();
 
  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList node_list=g.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=node_list.begin(), NLE=node_list.end(); 
	NLI!=NLE; ++NLI){
      Edge ed(*NI, NLI->element,NLI->weight,NLI->randId);
      if(!(t.hasEdgeAndWt(ed))){
	Increment[ed]=0;;
      }
    }
  }

  Edge *ed=new Edge();
  inc_DFS(g,t,Increment, 0, g.getRoot(), *ed);

  for(vector<Node *>::iterator NI=allNodes.begin(), NE=allNodes.end(); NI!=NE; 
      ++NI){
    Graph::nodeList node_list=g.getNodeList(*NI);
    for(Graph::nodeList::iterator NLI=node_list.begin(), NLE=node_list.end(); 
	NLI!=NLE; ++NLI){
      Edge ed(*NI, NLI->element,NLI->weight, NLI->randId);
      if(!(t.hasEdgeAndWt(ed))){
	int wt=ed.getWeight();
	Increment[ed]+=wt;
      }
    }
  }

  return Increment;
}

//push it up: TODO
const graphListElement *findNodeInList(const Graph::nodeList &NL,
					      Node *N);

graphListElement *findNodeInList(Graph::nodeList &NL, Node *N);
//end TODO

//Based on edgeIncrements (above), now obtain
//the kind of code to be inserted along an edge
//The idea here is to minimize the computation
//by inserting only the needed code
static void getCodeInsertions(Graph &g, map<Edge, getEdgeCode *, EdgeCompare> &instr,
                              vector<Edge > &chords, 
                              map<Edge,int, EdgeCompare> &edIncrements){

  //Register initialization code
  vector<Node *> ws;
  ws.push_back(g.getRoot());
  while(ws.size()>0){
    Node *v=ws.back();
    ws.pop_back();
    //for each edge v->w
    Graph::nodeList succs=g.getNodeList(v);
    
    for(Graph::nodeList::iterator nl=succs.begin(), ne=succs.end();
	nl!=ne; ++nl){
      int edgeWt=nl->weight;
      Node *w=nl->element;
      //if chords has v->w
      Edge ed(v,w, edgeWt, nl->randId);
      bool hasEdge=false;
      for(vector<Edge>::iterator CI=chords.begin(), CE=chords.end();
	  CI!=CE && !hasEdge;++CI){
	if(*CI==ed && CI->getWeight()==edgeWt){//modf
	  hasEdge=true;
	}
      }

      if(hasEdge){//so its a chord edge
	getEdgeCode *edCd=new getEdgeCode();
	edCd->setCond(1);
	edCd->setInc(edIncrements[ed]);
	instr[ed]=edCd;
      }
      else if(g.getNumberOfIncomingEdges(w)==1){
	ws.push_back(w);
	//std::cerr<<"Added w\n";
      }
      else{
	getEdgeCode *edCd=new getEdgeCode();
	edCd->setCond(2);
	edCd->setInc(0);
	instr[ed]=edCd;
	//std::cerr<<"Case 2\n";
      }
    }
  }

  /////Memory increment code
  ws.push_back(g.getExit());
  
  while(!ws.empty()) {
    Node *w=ws.back();
    ws.pop_back();


    ///////
    //vector<Node *> lt;
    vector<Node *> lllt=g.getAllNodes();
    for(vector<Node *>::iterator EII=lllt.begin(); EII!=lllt.end() ;++EII){
      Node *lnode=*EII;
      Graph::nodeList &nl = g.getNodeList(lnode);
      graphListElement *N = findNodeInList(nl, w);
      if (N){	
	Node *v=lnode;

	//if chords has v->w
	Edge ed(v,w, N->weight, N->randId);
	getEdgeCode *edCd=new getEdgeCode();
	bool hasEdge=false;
	for(vector<Edge>::iterator CI=chords.begin(), CE=chords.end(); CI!=CE;
	    ++CI){
	  if(*CI==ed && CI->getWeight()==N->weight){
	    hasEdge=true;
	    break;
	  }
	}
	if(hasEdge){
	  char str[100];
	  if(instr[ed]!=NULL && instr[ed]->getCond()==1){
	    instr[ed]->setCond(4);
	  }
	  else{
	    edCd->setCond(5);
	    edCd->setInc(edIncrements[ed]);
	    instr[ed]=edCd;
	  }
	  
	}
	else if(g.getNumberOfOutgoingEdges(v)==1)
	  ws.push_back(v);
	else{
	  edCd->setCond(6);
	  instr[ed]=edCd;
	}
      }
    }
  }
  ///// Register increment code
  for(vector<Edge>::iterator CI=chords.begin(), CE=chords.end(); CI!=CE; ++CI){
    getEdgeCode *edCd=new getEdgeCode();
    if(instr[*CI]==NULL){
      edCd->setCond(3);
      edCd->setInc(edIncrements[*CI]);
      instr[*CI]=edCd;
    }
  }
}

//Add dummy edges corresponding to the back edges
//If a->b is a backedge
//then incoming dummy edge is root->b
//and outgoing dummy edge is a->exit
//changed
void addDummyEdges(vector<Edge > &stDummy, 
		   vector<Edge > &exDummy, 
		   Graph &g, vector<Edge> &be){
  for(vector<Edge >::iterator VI=be.begin(), VE=be.end(); VI!=VE; ++VI){
    Edge ed=*VI;
    Node *first=ed.getFirst();
    Node *second=ed.getSecond();
    g.removeEdge(ed);

    if(!(*second==*(g.getRoot()))){
      Edge *st=new Edge(g.getRoot(), second, ed.getWeight(), ed.getRandId());
      stDummy.push_back(*st);
      g.addEdgeForce(*st);
    }

    if(!(*first==*(g.getExit()))){
      Edge *ex=new Edge(first, g.getExit(), ed.getWeight(), ed.getRandId());
      exDummy.push_back(*ex);
      g.addEdgeForce(*ex);
    }
  }
}

//print a given edge in the form BB1Label->BB2Label
void printEdge(Edge ed){
  cerr<<((ed.getFirst())->getElement())
    ->getName()<<"->"<<((ed.getSecond())
			  ->getElement())->getName()<<
    ":"<<ed.getWeight()<<" rndId::"<<ed.getRandId()<<"\n";
}

//Move the incoming dummy edge code and outgoing dummy
//edge code over to the corresponding back edge
static void moveDummyCode(vector<Edge> &stDummy, 
                          vector<Edge> &exDummy, 
                          vector<Edge> &be,  
                          map<Edge, getEdgeCode *, EdgeCompare> &insertions, 
			  Graph &g){
  typedef vector<Edge >::iterator vec_iter;
  
  map<Edge,getEdgeCode *, EdgeCompare> temp;
  //iterate over edges with code
  std::vector<Edge> toErase;
  for(map<Edge,getEdgeCode *, EdgeCompare>::iterator MI=insertions.begin(), 
	ME=insertions.end(); MI!=ME; ++MI){
    Edge ed=MI->first;
    getEdgeCode *edCd=MI->second;

    ///---new code
    //iterate over be, and check if its starts and end vertices hv code
    for(vector<Edge>::iterator BEI=be.begin(), BEE=be.end(); BEI!=BEE; ++BEI){
      if(ed.getRandId()==BEI->getRandId()){
	
	if(temp[*BEI]==0)
	  temp[*BEI]=new getEdgeCode();
	
	//so ed is either in st, or ex!
	if(ed.getFirst()==g.getRoot()){

	  //so its in stDummy
	  temp[*BEI]->setCdIn(edCd);
	  toErase.push_back(ed);
	}
	else if(ed.getSecond()==g.getExit()){

	  //so its in exDummy
	  toErase.push_back(ed);
	  temp[*BEI]->setCdOut(edCd);
	}
	else{
	  assert(false && "Not found in either start or end! Rand failed?");
	}
      }
    }
  }
  
  for(vector<Edge >::iterator vmi=toErase.begin(), vme=toErase.end(); vmi!=vme; 
      ++vmi){
    insertions.erase(*vmi);
    g.removeEdgeWithWt(*vmi);
  }
  
  for(map<Edge,getEdgeCode *, EdgeCompare>::iterator MI=temp.begin(), 
      ME=temp.end(); MI!=ME; ++MI){
    insertions[MI->first]=MI->second;
  }
    
#ifdef DEBUG_PATH_PROFILES
  cerr<<"size of deletions: "<<toErase.size()<<"\n";
  cerr<<"SIZE OF INSERTIONS AFTER DEL "<<insertions.size()<<"\n";
#endif

}

//Do graph processing: to determine minimal edge increments, 
//appropriate code insertions etc and insert the code at
//appropriate locations
void processGraph(Graph &g, 
		  Instruction *rInst, 
		  Instruction *countInst, 
		  vector<Edge >& be, 
		  vector<Edge >& stDummy, 
		  vector<Edge >& exDummy, 
		  int numPaths){

  static int MethNo=-1;
  MethNo++;
  //Given a graph: with exit->root edge, do the following in seq:
  //1. get back edges
  //2. insert dummy edges and remove back edges
  //3. get edge assignments
  //4. Get Max spanning tree of graph:
  //   -Make graph g2=g undirectional
  //   -Get Max spanning tree t
  //   -Make t undirectional
  //   -remove edges from t not in graph g
  //5. Get edge increments
  //6. Get code insertions
  //7. move code on dummy edges over to the back edges
  

  //This is used as maximum "weight" for 
  //priority queue
  //This would hold all 
  //right as long as number of paths in the graph
  //is less than this
  const int INFINITY=99999999;


  //step 1-3 are already done on the graph when this function is called
  DEBUG(printGraph(g));

  //step 4: Get Max spanning tree of graph

  //now insert exit to root edge
  //if its there earlier, remove it!
  //assign it weight INFINITY
  //so that this edge IS ALWAYS IN spanning tree
  //Note than edges in spanning tree do not get 
  //instrumented: and we do not want the
  //edge exit->root to get instrumented
  //as it MAY BE a dummy edge
  Edge ed(g.getExit(),g.getRoot(),INFINITY);
  g.addEdge(ed,INFINITY);
  Graph g2=g;

  //make g2 undirectional: this gives a better
  //maximal spanning tree
  g2.makeUnDirectional();
  DEBUG(printGraph(g2));

  Graph *t=g2.getMaxSpanningTree();
#ifdef DEBUG_PATH_PROFILES
  std::cerr<<"Original maxspanning tree\n";
  printGraph(*t);
#endif
  //now edges of tree t have weights reversed
  //(negative) because the algorithm used
  //to find max spanning tree is 
  //actually for finding min spanning tree
  //so get back the original weights
  t->reverseWts();

  //Ordinarily, the graph is directional
  //lets converts the graph into an 
  //undirectional graph
  //This is done by adding an edge
  //v->u for all existing edges u->v
  t->makeUnDirectional();

  //Given a tree t, and a "directed graph" g
  //replace the edges in the tree t with edges that exist in graph
  //The tree is formed from "undirectional" copy of graph
  //So whatever edges the tree has, the undirectional graph 
  //would have too. This function corrects some of the directions in 
  //the tree so that now, all edge directions in the tree match
  //the edge directions of corresponding edges in the directed graph
  removeTreeEdges(g, *t);

#ifdef DEBUG_PATH_PROFILES
  cerr<<"Final Spanning tree---------\n";
  printGraph(*t);
  cerr<<"-------end spanning tree\n";
#endif

  //now remove the exit->root node
  //and re-add it with weight 0
  //since infinite weight is kinda confusing
  g.removeEdge(ed);
  Edge edNew(g.getExit(), g.getRoot(),0);
  g.addEdge(edNew,0);
  if(t->hasEdge(ed)){
    t->removeEdge(ed);
    t->addEdge(edNew,0);
  }

  DEBUG(printGraph(g);
        printGraph(*t));

  //step 5: Get edge increments

  //Now we select a subset of all edges
  //and assign them some values such that 
  //if we consider just this subset, it still represents
  //the path sum along any path in the graph

  map<Edge, int, EdgeCompare> increment=getEdgeIncrements(g,*t);
#ifdef DEBUG_PATH_PROFILES
  //print edge increments for debugging
  
  for(map<Edge, int, EdgeCompare>::iterator M_I=increment.begin(), M_E=increment.end(); 
      M_I!=M_E; ++M_I){
    printEdge(M_I->first);
    cerr<<"Increment for above:"<<M_I->second<<"\n";
  }
#endif

 
  //step 6: Get code insertions
  
  //Based on edgeIncrements (above), now obtain
  //the kind of code to be inserted along an edge
  //The idea here is to minimize the computation
  //by inserting only the needed code
  vector<Edge> chords;
  getChords(chords, g, *t);


  //cerr<<"Graph before getCodeInsertion:\n";
  //printGraph(g);
  map<Edge, getEdgeCode *, EdgeCompare> codeInsertions;
  getCodeInsertions(g, codeInsertions, chords,increment);
  
#ifdef DEBUG_PATH_PROFILES
  //print edges with code for debugging
  cerr<<"Code inserted in following---------------\n";
  for(map<Edge, getEdgeCode *>::iterator cd_i=codeInsertions.begin(), 
	cd_e=codeInsertions.end(); cd_i!=cd_e; ++cd_i){
    printEdge(cd_i->first);
    cerr<<cd_i->second->getCond()<<":"<<cd_i->second->getInc()<<"\n";
  }
  cerr<<"-----end insertions\n";
#endif

  //step 7: move code on dummy edges over to the back edges

  //Move the incoming dummy edge code and outgoing dummy
  //edge code over to the corresponding back edge

  moveDummyCode(stDummy, exDummy, be, codeInsertions, g);
  
#ifdef DEBUG_PATH_PROFILES
  //debugging info
  cerr<<"After moving dummy code\n";
  for(map<Edge, getEdgeCode *>::iterator cd_i=codeInsertions.begin(), 
	cd_e=codeInsertions.end(); cd_i != cd_e; ++cd_i){
    printEdge(cd_i->first);
    cerr<<cd_i->second->getCond()<<":"
	<<cd_i->second->getInc()<<"\n";
  }
  cerr<<"Dummy end------------\n";
#endif


  //see what it looks like...
  //now insert code along edges which have codes on them
  for(map<Edge, getEdgeCode *>::iterator MI=codeInsertions.begin(), 
	ME=codeInsertions.end(); MI!=ME; ++MI){
    Edge ed=MI->first;
    insertBB(ed, MI->second, rInst, countInst, numPaths, MethNo);
  } 
}

//print the graph (for debugging)
void printGraph(Graph &g){
  vector<Node *> lt=g.getAllNodes();
  cerr<<"Graph---------------------\n";
  for(vector<Node *>::iterator LI=lt.begin(); 
      LI!=lt.end(); ++LI){
    cerr<<((*LI)->getElement())->getName()<<"->";
    Graph::nodeList nl=g.getNodeList(*LI);
    for(Graph::nodeList::iterator NI=nl.begin(); 
	NI!=nl.end(); ++NI){
      cerr<<":"<<"("<<(NI->element->getElement())
	->getName()<<":"<<NI->element->getWeight()<<","<<NI->weight<<","
	  <<NI->randId<<")";
    }
    cerr<<"\n";
  }
  cerr<<"--------------------Graph\n";
}
