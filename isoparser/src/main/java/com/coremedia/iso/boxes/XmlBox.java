package com.coremedia.iso.boxes;

import com.coremedia.iso.*;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public class XmlBox extends AbstractFullBox {
    String xml;
    public static final String TYPE = "xml ";

    public XmlBox() {
        super(IsoFile.fourCCtoBytes(TYPE));
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    @Override
    protected long getContentSize() {
        return 4 + Utf8.utf8StringLengthInBytes(xml);
    }

    @Override
    public void _parseDetails() {
        parseVersionAndFlags();
        xml = IsoTypeReader.readString(content, content.remaining());
    }

    @Override
    protected void getContent(ByteBuffer bb) throws IOException {
        writeVersionAndFlags(bb);
        bb.put(Utf8.convert(xml));
    }
}
