package co.istad.makara.pipeline.stream;

public class DataXmlDeserializer extends XmlStringDeserializer<Data>{

    public DataXmlDeserializer() {
        super(Data.class);
    }
}
