package com.ruoyi.kcon.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ruoyi.common.exception.ServiceException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具（基于 ZXing，纯本地生成，离线现场可用）
 *
 * @author kcon
 */
public class QrCodeUtil
{
    private QrCodeUtil()
    {
    }

    /**
     * 生成二维码 PNG 并写入输出流
     *
     * @param content 内容（通常是签到页 URL）
     * @param width   宽度（px）
     * @param height  高度（px）
     * @param out     输出流
     */
    public static void writePng(String content, int width, int height, OutputStream out)
    {
        try
        {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++)
            {
                for (int y = 0; y < height; y++)
                {
                    image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ImageIO.write(image, "PNG", out);
            out.flush();
        }
        catch (Exception e)
        {
            throw new ServiceException("二维码生成失败：" + e.getMessage());
        }
    }
}
