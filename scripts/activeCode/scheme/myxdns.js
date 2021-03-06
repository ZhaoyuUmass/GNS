/**
 * Created by gaozy on 12/27/16.
 */

function distance(lat1, lon1, lat2, lon2) {
    if(lat1 == undefined || lat2 == undefined) return Number.MAX_VALUE;
    var R = 6371; // Radius of the earth in km
    var dLat = (lat2 - lat1) * Math.PI / 180;  // deg2rad below
    var dLon = (lon2 - lon1) * Math.PI / 180;
    var a =
        0.5 - Math.cos(dLat)/2 +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        (1 - Math.cos(dLon))/2;

    return R * 2 * Math.asin(Math.sqrt(a));
}

function sum(x, y){
    return x+y;
}

function run(value, accessor, querier) {
    
    var records = value["A"]["record"],
        client = value["client_ip"],
        weight = querier.readGuid(null, "weight")["weight"],
        locs = records.slice(),
        w = [],
        indexes = [],
        dist = [],
        i = 0;

    if(client == undefined){
        // if client ip does not exist, fetch a ip from the test field
        client = querier.readGuid(null, "testIp")["testIp"];
    }
    
    // query the location info through geoip for all ip addresses
    locs.push(client);
    var coords = querier.getLocations(locs); // the returned value is formatted as {ip1: {"latitude":lat1, "longitude":lng1},...}
	
	// do not calculate the distance for client
    for(i=0; i<records.length; i++){
        dist.push(Math.round(distance(coords[records[i]]["latitude"], coords[records[i]]["longitude"],
            coords[client]["latitude"], coords[client]["longitude"])));
    }
	
    // figure out all candidates
    var minimal_distance = Math.min.apply(Math, dist),
        i = -1;
        
    while ((i = dist.indexOf(minimal_distance, i+1)) != -1){
        indexes.push(i);
        w.push(weight[i]);
    }
	
    // figure out the weight for all candidates
    var total = w.reduce(sum);
    w.forEach(function(element, index){w[index] = element/total});

    // get the index of the returned replica
    var r = Math.random(),
        i = -1;
    while (r>=0){
        i++;
        r = r - w[i];
    }
      
    // strip the replicas that have not been chosen
    records.splice(indexes[i]+1, records.length);
    records.splice(0, indexes[i]);
    
    return value;
}