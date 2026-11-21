import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;

/**
 * Copies a mod jar, removing the Mixin injector annotations from one handler method so Mixin never tries to apply it
 * (the method stays, unused). For third-party Fabric mods whose single injector targets vanilla code NeoForge
 * replaced, which makes Connector refuse to start the game; Patchwork then does that injector's job the NeoForge way.
 * Usage: java -cp asm.jar StripMixinMember.java in.jar out.jar path/to/Mixin.class methodName
 */
public class StripMixinMember {
    public static void main(String[] a) throws Exception {
        String entry = a[2], method = a[3];
        int[] stripped = {0};
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(Path.of(a[0])));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(Path.of(a[1])))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                byte[] data = in.readAllBytes();
                if (e.getName().equals(entry)) {
                    ClassReader reader = new ClassReader(data);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                            MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                            if (!name.equals(method)) return mv;
                            return new MethodVisitor(Opcodes.ASM9, mv) {
                                @Override
                                public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                                    if (d.startsWith("Lorg/spongepowered/asm/mixin/injection/")) { stripped[0]++; return null; }
                                    return super.visitAnnotation(d, visible);
                                }
                            };
                        }
                    }, 0);
                    data = writer.toByteArray();
                }
                ZipEntry copy = new ZipEntry(e.getName());
                copy.setTime(e.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }
        }
        System.out.println("stripped " + stripped[0] + " injector annotation(s) from " + entry + "#" + method);
        if (stripped[0] == 0) System.exit(1);
    }
}
