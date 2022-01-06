# HyppoTunes
HyppoTunes mobile app.
 
Connects to the HyppoTunesServer via a gRPC connection. 
 
Uses a SQLite database to store data about songs downloaded from the HyppoTunesServer.<br />
Inserts a new row in the database each time a song is downloaded to the app storage.<br />
Updates all songs in the database automatically each time the app is run based on what songs there are in the app storage.<br />
Reads all song data from the database and presents it to the user via a RecyclerView.<br />
Deletes a song from the database and the app storage whenever the user requests it.<br />

Plays a song every time the user selects one from the RecyclerView.<br />
It then keeps playing as a foreground service.
