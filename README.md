# Descrizione del progetto

Lo scopo del nostro progetto è individuare all’interno di un insieme di proteine delle interazioni fra coppie di esse. Laddove per “interazioni” intendiamo analogie fisiche oppure funzionali. Per farlo partiamo innanzitutto considerando questo insieme come un grafo, con le proteine al posto di nodi e le loro interazioni al posto degli archi.
Affrontare questo argomento dal punto di vista computazionale significa raggruppare i nodi del grafo per mettere in evidenza queste relazioni.
La rilevazione di queste possibili interazioni è cruciale poiché la rete PPI (protein-protein interaction) è una grande fonte di informazioni, fondamentali per vari studi biologici. 

Quello che facciamo, dal punto di vista teorico, è partire da un nodo  e calcolare la distanza minima, tenendo conto del percorso fatto, con ogni altro nodo. A questo punto possiamo calcolare la frazione di percorsi minimi fra due nodi che passano attraverso un determinato arco sui percorsi minimi totali e fare la somma di questo valore per ogni arco. Questo stabilirà la Betweenness di ogni arco. 
La betweenness può essere pensata come il grado di “traffico” che passa per ogni arco. 
L’arco con la betweenness massima viene eliminato dal grafo, perché è molto probabile che quell’arco connetta due possibili cluster. 
A questo punto ripetiamo questi passaggi finché abbiamo archi a disposizione. Questo renderà il grafo sconnesso e poi aumenterà sempre di più il numero di componenti connesse. Queste componenti saranno i nostri cluster. 

---
# Il nostro dataset

I dataset reali disponibili sono nel sito [dip.doe-mbi.ucla.edu/dip](https://www.dip.doe-mbi.ucla.edu/dip.it/) da cui è tratto in particolare quello da noi utilizzato: R.Norv - 666 proteine, 619 iterazioni.
Per ottimizzare i risultati, nel grafo abbiamo deciso di eliminare le iterazioni del tipo (a)->(a) e soprattutto abbiamo limitato l’algoritmo a considerare le componenti connesse
con un numero di elementi superiore a 10: nel nostro caso il numero di componenti cala drasticamente, da 177 a 15.




# Classi
Il nostro algoritmo è costituito da 12 classi Java. Segue una breve descrizione di ogni classe:

## JcomeJava
Si tratta della classe main del nostro progetto.
Metodi:
- CreateInput:
Input: JavaPairRDD di archi;
NB:  	Struttura di una singola proteina:
NodeId  Root  Neighbors | Distance | Color | Path
Per ogni arco (a,b) vengono generate le coppie (a,lista=<b>) e (b,lista=<a>);
Il prodotto cartesiano con tutti i nodi restituisce triplette (nodo, nodo, lista di vicini);
Attraverso il reduce step, si ottengono le corrette liste di vicini;
I campi mancanti vengono inizializzati con la tripletta (0, "WHITE", null);
Output: File di input n x n.
- ForwardMR
	Input: JavaRDD di protein;
	ForwardMR;
Pairing;
GetNeigh;
Recontruct;
	Output: JavaRDD di protein;
- BackwardMR
	Input: numero step, file di input della singola componente (esplorato)
Si considerano tutti gli archi;
	Si prendono le coppie (arco,1);
	Attraverso il reduce step si ottengono le coppie (arco, numero di occorrenze);
Dividendo per n*(n-1) il valore della coppia, si ottengono (arco, betweenness);
EdgesComparator restituisce l’arco con betweenness massima.
	Output: (numero step, lista di archi della componente) , (arco, betweenness) 
- ComputeBC
	Input: lista (step, lista di archi della componente) , (arco, betweenness)
	EdgesComparator l’arco con betweenness massima tra quelli con betweenness
	massima nella propria componente.
	Output: quartetto (step, lista di archi della componente) , (arco, betweenness)



Data l’onerosità del codice, il numero massimo di iterazioni è impostato a 5.


Interfaccia Neo4J: creazione del grafo di partenza;
Algoritmo:
>Lista BC: ad ogni iterazione dell’algoritmo salvo il grafo ottimo corrispondente;
>Ciclo while (in questo caso la limitazione è dato dal numero massimo di step)
> DividiComponentiCheck;
	> Check;
	>Per ogni componente connessa:
	- CreateInput;
	- Inizializzazione dei nodi dove NodeId=Root -> Color = “GREY”;
	- Ciclo while (finché tutta la componente non viene esplorata)
		ForwardMR;
	- Calcolo della betweeness attraverso BackwardMR.
	>Calcolo della misura BC attraverso ComputeBC;
	>Eliminazione dell’arco con betweeness massima;
	>Aggiungo alla lista BC il risultato ottenuto.
>Definisco l’oggetto Q=(step, lista di componenti);
>Q2 è il prodotto cartesiano tra Q e la lista di tutti gli archi;
>ComputeDamnQ
>QComparator



DividiComponentiCheck
Input: Lista di archi;
Divisione degli archi in componenti connesse, basata sulla verifica effettuata dal metodo check (fissato un arco e1=(a,b), questo viene comparato ad un altro arco e2=(c,d): se a o b compare in e2 il metodo ritorna true; altrimenti falso).
Output: Lista di liste di archi;

## Check
Input: Lista di archi.
Controllo per evitare che ci siano archi che si ripetano.
Output: Lista di archi.


## ForwardMR
(FlatMapFunction)
Input: Proteina
Per l’esplorazione del grafo.
Il codice è eseguito solo quando la proteina in questione è di colore GREY:
>Il nodo viene esaminato;
>Se esiste, si estrapola il path corrente;
>Per ogni nodo nella lista dei neighbors:
Si genera la proteina
(nodo neighbors corrente, nodo esaminato, null | 
distanza corrente +1, GREY, path corrente + arco (nodo neigh, nodo esaminato) );
>Si genera una nuova proteina a quella di partenza, ma di colore BLACK;
Output: Lista di proteine

## Pairing
(PairFunction)
Input: Proteine
La struttura monolitica < NodeId  Root  Neighbors | Distance | Color | Path > viene divisa in una coppia < (NodeId  Root), (Neighbors | Distance | Color | Path) >
Output: Proteine (destrutturate)

## GetNeigh
(ReduceByKey)
Input: Proteine (destrutturate: < (NodeId  Root), (Neighbors | Distance | Color | Path) >)
Identificate dalla chiave (NodeId, Root), la classe recupera il quartetto di valori privilegando quelli generati dalla classe ForwardMR, aggiornati, piuttosto che quelli originali.
Output: Proteine (destrutturate)

## Recontruct
Input: Proteine (destrutturate: < (NodeId  Root), (Neighbors | Distance | Color | Path) >)
Si torna alla struttura monolitica < NodeId  Root  Neighbors | Distance | Color | Path >.
Output: Proteine 

## EdgesComparator
(Comparator)
Input: coppie (arco, betweeness)
Individuazione della betweeness massima.
Output: (arco, betweeness)

## ComputeDamnQ
Input: coppie (lista delle componenti, lista di tutti gli archi)
Siano E=|{tutti gli archi}|, C=|{componenti connessa}|, 
costruisco la matrice f dove f [ i ][ j ] =
f [ i ][ i ] = numero di archi della componente i / E, se i=j;
f [ i ][ j ] = f [ j ][ i ] = damnEdges / E, se i!=j.
Calcolo Q attraverso DamnQ.
Output: (lista delle componenti, Q)
>damnEdges 
Input: (archi della componente i, archi della componente j, tutti gli archi)
Rimozione degli archi delle componenti i e j da tutti gli archi;
Conteggio degli archi rimanenti connessi con le due componenti attraverso Link.
Output: conteggio
>>Link
Input: (arco (a,b), lista della componente i , lista della componente j)
Output: TRUE se a o b compare in una delle componenti 
>DamnQ
Input: (numero di componenti, matrice f)
Calcolo di Q.
Output: Q




QComparator
(Comparator)
Input: coppie (lista delle componenti, Q)
Individuazione della Q massima.
Output: (lista delle componenti, Q)
