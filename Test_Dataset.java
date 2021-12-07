package JcomeJulian;

import java.util.*;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import scala.Tuple3;
import scala.Tuple4;

public class Test_Dataset {

	private static JavaRDD<Protein> createInput(JavaPairRDD<String, String> Edges) {
		
		
		  /********************METODI********************/

		/*
		 * Per ogni arco (a,b) 
		 * Estrapolo le coppie <NodeId,Neighbor>
		 * 					   <a,b> e <b,a>
		 */
		JavaPairRDD<String,ArrayList<String>> input1 = Edges.mapToPair(e -> {
	    	ArrayList<String> boh = new ArrayList<String>(); boh.add(e._2);
			return new Tuple2<String,ArrayList<String>>(e._1,boh);}	);
	    JavaPairRDD<String,ArrayList<String>> input2 = Edges.mapToPair(e -> {
	    	ArrayList<String> boh = new ArrayList<String>(); boh.add(e._1);
			return new Tuple2<String,ArrayList<String>>(e._2,boh);}	);
	    input1 = input1.union(input2);
		
	    // Preparo la struttura dei neighbors come lista
		JavaPairRDD<String,ArrayList<String>> input3 = input1.mapToPair(pair -> {
			String nodeId = pair._1;		ArrayList<String> neigh = pair._2;
			Tuple2<String,ArrayList<String>> result = new Tuple2<String,ArrayList<String>>(nodeId,neigh);
			return result;});
		
		/*
		 * Costruisco la matrice n x n
		 */
		JavaRDD<String> Nodes = Edges.keys().union(Edges.values()).distinct();
    	JavaPairRDD<String, Tuple2<String, ArrayList<String>>> NNN = Nodes.cartesian(input3); 
		//Separo <NodeId,Root> e <Neighbors>
    	JavaPairRDD<Tuple2<String, String>, ArrayList<String>> NNN2 = NNN.mapToPair(nnn -> {
			String n2=nnn._1;
			Tuple2<String, ArrayList<String>> nnn2=nnn._2;
			String n1 = nnn2._1;
			Tuple2<String, String> pair = new Tuple2<String, String>(n1,n2);
			ArrayList<String> neigh = nnn2._2;
			Tuple2<Tuple2<String, String>, ArrayList<String>> result = new Tuple2<Tuple2<String, String>, ArrayList<String>>(pair,neigh);
			return result;
		});
    	//Accorpo i neighbors
		NNN2 = NNN2.reduceByKey((n1,n2)-> {	ArrayList<String> neigh = new ArrayList<String>();
											if(!n1.isEmpty())neigh.addAll(n1); if(!n2.isEmpty())neigh.addAll(n2);
											return neigh; });
		
		/*
		 * Definisco la struttura di ogni riga della matrice n x n
		 */
		JavaRDD<Protein> input = NNN2.map(nnn -> {
			Tuple2<String, String> pair = nnn._1;
			Tuple3<String, String, ArrayList<String>> key = new Tuple3<String, String, ArrayList<String>>(pair._1, pair._2, nnn._2);
			Tuple3<Integer, String, ArrayList<Tuple2<String,String>>> value = 
					new Tuple3<Integer, String, ArrayList<Tuple2<String, String>>>(0, "WHITE", null);
			Protein p = new Protein(key, value);
			return p;
			});
		
		return input;
	}

	private static void printInput(List<Protein> collect) {
		for(int i = 1; i<=collect.size(); i++) {
			for(int j = 1; j<=collect.size(); j++) {
				for(Protein p:collect) {
						if(p.getKey()._2().equals(Integer.toString(i))&&p.getKey()._1().equals(Integer.toString(j))) 
						printProtein(p);
	}}}}
	
	/*
	 * Struttura di una singola proteina:
	 * NodeId  Root  Neighbors | Distance | Color | Path
	 */
	public static void printProtein(Protein p) {
		Tuple3<String, String, ArrayList<String>> Key = p.getKey();
		Tuple3<Integer, String, ArrayList<Tuple2<String, String>>> Value = p.getValue();
		System.out.println("\t"+Key._1()+"\t"+Key._2()+"\t"+Key._3()+
				"\t"+Value._1()+"\t"+Value._2()+"\t"+Value._3());
	}

	private static JavaRDD<Protein> ForwardMR(JavaRDD<Protein> While) {
		
		//Eseguo il ForwardMR
		JavaRDD<Protein> Forward1 = While.flatMap(new ForwardMR());
		Forward1 = While.union(Forward1);
		//Prendo come chiave <NodeId,Root> per ottenere le nuove informazioni dall'output del ForwardMR
		JavaPairRDD<Tuple2<String,String>, Tuple4<ArrayList<String>,Integer,String,ArrayList<Tuple2<String,String>>>>
			Forward2 = Forward1.mapToPair(new Pairing());
		Forward2 = Forward2.reduceByKey(new getNeigh());
		//Ripristino la struttura della proteina
		Forward1=Forward2.map(new Reconstruct());
		return Forward1;
		
	}

	private static Tuple2<Tuple2<Integer, List<List<Tuple2<String, String>>>>, Tuple2<Tuple2<String, String>, Float>> BackwardMR
	(Integer step, JavaRDD<Protein> Results) {
	
		//Prendo tutti gli archi
		JavaRDD<ArrayList<Tuple2<String, String>>> archi0 = Results.map(p -> p.getValue()._3());
		archi0 = archi0.filter(a->a!=null);
		JavaRDD<Tuple2<String, String>> archiRDD = archi0.flatMap(l -> l.iterator());
		//Ordino gli archi
		archiRDD = archiRDD.map(a -> {
			int compare = a._1.compareTo(a._2);  
			if (compare > 0) return new Tuple2<String, String>(a._2,a._1);
			return a;
		});
		//Mi salvo gli archi che compongono la componente connessa
		List<Tuple2<String,String>> edgesComp = archiRDD.distinct().collect();
		List<List<Tuple2<String,String>>> componente = new ArrayList<List<Tuple2<String,String>>>();
		componente.add(edgesComp);
		/*
		 * BETWEENESS:
		 * BC(e) = sum(d_s,t(e))
		 * e edge, d_s,t(e) = number of shortest path from s to t
		 * 							passing through the edge e 
		 * 					_______________________________________
		 * 
		 * 					  number of shortest path from s to t
		 * 
		 */
		//Conto le occorrenze degli archi
		JavaPairRDD<Tuple2<String, String>,Integer> archiRDD2 = 
				archiRDD.mapToPair(x->new Tuple2<Tuple2<String, String>,Integer>(x,1));
		archiRDD2 = archiRDD2.reduceByKey((x,y)->x+y);
		JavaRDD<String> nodiComp = Results.map(p -> p.getKey()._1()).distinct();
		Integer numeroNodiComp = (int)nodiComp.count();
		//Calcola la Betweeness
		JavaPairRDD<Tuple2<String, String>,Float> Beetweeness = 
				archiRDD2.mapToPair(x->new Tuple2<Tuple2<String, String>,Float>(x._1,(float)x._2/(numeroNodiComp*(numeroNodiComp-1)))); 
		Tuple2<Tuple2<String, String>,Float> currentBC  = Beetweeness.max(new EdgesComparator());
	
		return new Tuple2<Tuple2<Integer,List<List<Tuple2<String,String>>>>,Tuple2<Tuple2<String,String>,Float>>
				(new Tuple2<Integer,List<List<Tuple2<String,String>>>>(step,componente),currentBC);
	}
	
	private static Tuple2<Tuple2<Integer, List<List<Tuple2<String,String>>>>, Tuple2<Tuple2<String, String>, Float>> ComputeBC(
			JavaRDD<Tuple2<Tuple2<Integer, List<List<Tuple2<String,String>>>>, Tuple2<Tuple2<String, String>, Float>>> BCRDD) {
		
		//Trovo l'arco con betweeness massima tra le componenti connesse del grafo
		JavaPairRDD<Tuple2<Integer, List<List<Tuple2<String,String>>>>, Tuple2<Tuple2<String, String>, Float>> BCRDD2 = 
				BCRDD.mapToPair(bc->new Tuple2<Tuple2<Integer, List<List<Tuple2<String,String>>>>, Tuple2<Tuple2<String, String>, Float>>(bc._1,bc._2));
		JavaRDD<Tuple2<Tuple2<String, String>, Float>> BCRDD3 = BCRDD2.map(p->p._2);
		Tuple2<Tuple2<String, String>,Float> currentBC  = BCRDD3.max(new EdgesComparator());
		Tuple2<Tuple2<Integer, List<List<Tuple2<String,String>>>>, Tuple2<Tuple2<String, String>, Float>> maxBC = 
				BCRDD2.filter(bc->bc._2.equals(currentBC)).first();
		return maxBC;
		
		}

	
	
	public static void main(String[] args) {

		Logger.getLogger("org").setLevel(Level.ERROR);
        Logger.getLogger("akka").setLevel(Level.ERROR);
        SparkConf sc = new SparkConf().setMaster("local[*]").setAppName("prova");       
        JavaSparkContext jsc = new JavaSparkContext(sc);

        System.out.println( "\n\n\t\t\t\t ___________________________\n"
        					+ "\t\t\t\t|   _____________________   |\n"
			        		+ "\t\t\t\t|  |   _______________   |  |\n"
							+ "\t\t\t\t|  |  |               |  |  |\n"
							+ "\t\t\t\t|  |  | J COME JULIAN |  |  |\n"
							+ "\t\t\t\t|  |  |  J COME JAVA  |  |  |\n"
         				   	+ "\t\t\t\t|  |  |_______________|  |  |\n"
         				   	+ "\t\t\t\t|  |_____________________|  |\n"
        					+ "\t\t\t\t|___________________________|\n");
        
       
        
        
        /********************DATA LOADING & PREPROCESSING********************/

    
        
    	JavaPairRDD<String,String> Edges, EdgesOriginal;
		System.out.println("\n\n\n_______________________________________________________________________________________________________________________________________________________________\n\n\n");
        
    	for(int which=1;which<=8;which++) {
    	//Dataset reale
    		JavaRDD<String> data = null;
            
    	System.out.println(
				           "\t\t\t    @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
				         + "\t\t\t    @@@                                @@\n"
	  					 + "\t\t\t    @@@                                @@\n"
	  					 + "\t\t\t    @@@          DATASET REALE         @@\n"
	  					 + "\t\t\t    @@@                                @@\n"
	  					 + "\t\t\t    @@@                                @@\n"
	  			         + "\t\t\t    @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
					     + "\t\t\t    @@@                                @@");
    	if(which==1) {
			System.out.println("\t\t\t    @@@ 1. Rnorv (666 nodi, 619 archi) @@");
			data = jsc.textFile("data/Rnorv20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==2) {
			System.out.println("\t\t\t    @@@ 2. Mmusc                       @@");
			data = jsc.textFile("data/Mmusc20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==3) {
			System.out.println("\t\t\t    @@@ 3. Hpylo                       @@");
			data = jsc.textFile("data/Hpylo20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==4) {
			System.out.println("\t\t\t    @@@ 4. Hsapi                       @@");
			data = jsc.textFile("data/Hsapi20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==5) {
			System.out.println("\t\t\t    @@@ 5. Celeg                       @@");
			data = jsc.textFile("data/Celeg20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==6) {
			System.out.println("\t\t\t    @@@ 6. Ecoli                       @@");
			data = jsc.textFile("data/Ecoli20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==7) {
			System.out.println("\t\t\t    @@@ 7. Dmela                       @@");
			data = jsc.textFile("data/Dmela20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
    	if(which==8) {
			System.out.println("\t\t\t    @@@ 8. Scere                       @@");
			data = jsc.textFile("data/Scere20170205.txt"); //Prendo in input il file txt e ottengo gli archi
		}
		System.out.println("\t\t\t    @@@                                @@\n"
						 + "\t\t\t    @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
						 + "\n\n");
		data = data.filter(l -> !l.startsWith("ID"));
		Edges = data.mapToPair(x -> new Tuple2<String,String>(x.split("\t")[0],x.split("\t")[1]));
		Edges = Edges.filter(e->!e._1.equals(e._2()));
		
		Integer num_nodi = (int)Edges.keys().union(Edges.values()).distinct().count();
		Integer num_archi = (int)Edges.count();
		
		/*
		 * Prendo gli archi e definisco le componenti
		 */
		
		JavaRDD<ArrayList<Tuple2<String,String>>> AllEdges = Edges.map(e->{
			ArrayList<Tuple2<String,String>> l = new ArrayList<Tuple2<String,String>>();
			l.add(e);
			return l;
		});
		JavaPairRDD<Integer,ArrayList<Tuple2<String,String>>> Edges3 = AllEdges.mapToPair(
				e->new Tuple2<Integer,ArrayList<Tuple2<String,String>>>(1,e) );
		Edges3 = Edges3.reduceByKey((l1,l2)->{l1.addAll(l2); return l1;});
		AllEdges = Edges3.map(e->e._2);
		
		//System.out.println("\n\nSTART: numero di archi: "+Edges.count());
		//System.out.println(AllEdges.collect());
				
//		System.out.println("\n\n\n\n@@@@@@@@@@@@@@@@@@@@@@@@DividiComponenti");
		JavaRDD<ArrayList<Tuple2<String, String>>> EdgesComp = AllEdges.flatMap(new DividiComponentiCheck());
		
		EdgesComp=EdgesComp.distinct().map(new Check()).distinct();
		
		//Elimino le componenti composte da pochi archi
		EdgesComp = EdgesComp.filter(comp->comp.size()>=10);
		
		Integer NumeroComponent = 0;
		NumeroComponent = EdgesComp.collect().size();
		System.out.println("\n\n\n\n\t\t@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n"
								 + "\t\t@@                                      @@\n"
								 + "\t\t@@ NUMERO DI NODI:  "+num_nodi+"                 @@\n"
								 + "\t\t@@ NUMERO DI ARCHI:  "+num_archi+"                @@"
									);
		if(NumeroComponent<10)System.out.println("\t\t@@  NUMERO DI COMPONENTI CONNESSE:   "+NumeroComponent+"  @@");
		else if(NumeroComponent<100)System.out.println("\t\t@@ NUMERO DI COMPONENTI CONNESSE:   "+NumeroComponent+"  @@");
		else System.out.println("\t\t@@ NUMERO DI COMPONENTI CONNESSE:   "+NumeroComponent+" @@");
		System.out.println("\t\t@@                                      @@\n"
				                 + "\t\t@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@\n\n"
				                 );
		Integer count=0;
		for(ArrayList<Tuple2<String,String>> comp : EdgesComp.collect()) {
			count++;
			System.out.println("\t"+count+". Numero di archi: "+comp.size());
		}
		System.out.println("\n\n\n\n\n");
		System.out.println("\n\n\n_______________________________________________________________________________________________________________________________________________________________\n\n\n");
				
    	}	
			
			
		
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			System.out.println( "\n\n\n\n\n\n\n\n\n"
					+ "\t\t\t\t ___________________________\n"
					+ "\t\t\t\t|   _____________________   |\n"
	        		+ "\t\t\t\t|  |   _______________   |  |\n"
					+ "\t\t\t\t|  |  |               |  |  |\n"
					+ "\t\t\t\t|  |  |  G  A  M  E   |  |  |\n"
					+ "\t\t\t\t|  |  |  O  V  E  R   |  |  |\n"
 				   	+ "\t\t\t\t|  |  |_______________|  |  |\n"
 				   	+ "\t\t\t\t|  |_____________________|  |\n"
					+ "\t\t\t\t|___________________________|\n");
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}