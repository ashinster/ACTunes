package animal.crossing.tunes.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbOffsetClient {
    private static final Logger log = LoggerFactory.getLogger(DbOffsetClient.class);

    public static final String TABLE_NAME = "db_offsets";
    public static final String PRIMARY_KEY = "deviceId";
    public static final String OFFSET_KEY = "offset";

    private AmazonDynamoDB client;
    private DynamoDB dynamoDB;
    private Table table;

    public DbOffsetClient(){
        client = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDB = new DynamoDB(client);
        table = dynamoDB.getTable(TABLE_NAME);
    }

    public void associateOffset(String deviceId, long offset){

        Item item = new Item()
                .withPrimaryKey(PRIMARY_KEY, deviceId)
                .withLong(OFFSET_KEY, offset);

        PutItemOutcome outcome = table.putItem(item);

        log.info("Put item response code: {}", outcome.getPutItemResult().getSdkHttpMetadata().getHttpStatusCode());
    }

    public Item retrieveItem(String deviceId){

        Item item = table.getItem(PRIMARY_KEY, deviceId);

        return item;
    }
}
