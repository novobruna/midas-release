package strober
package replay

import firrtl.ir.Circuit
import firrtl.passes.memlib._
import java.io.{File, FileWriter}

private class Compiler(confFile: File, macroFile: File) extends firrtl.VerilogCompiler {
  override def emitter = new midas.passes.MidasVerilogEmitter(confFile, macroFile)
}

object Compiler {
  def apply(chirrtl: Circuit, io: chisel3.Data, dir: File): Circuit = {
    dir.mkdirs
    val confFile = new File(dir, s"${chirrtl.main}.conf")
    val macroFile = new File(dir, s"${chirrtl.main}.macros.v")
    val annotations = new firrtl.AnnotationMap(Seq(
      firrtl.passes.memlib.InferReadWriteAnnotation(chirrtl.main),
      firrtl.passes.memlib.ReplSeqMemAnnotation(s"-c:${chirrtl.main}:-o:$confFile"),
      SeqMemPathAnnotation(chirrtl.main, confFile)))
    val verilog = new FileWriter(new File(dir, s"${chirrtl.main}.v"))
    val result = new Compiler(confFile, macroFile) compile (
      firrtl.CircuitState(chirrtl, firrtl.ChirrtlForm, Some(annotations)),
      verilog, Seq(
        new firrtl.passes.memlib.InferReadWrite,
        new firrtl.passes.memlib.ReplSeqMem,
        new SeqMemPathAnalysis(dir)))
    genVerilogFragment(chirrtl.main, io, new FileWriter(new File(dir, s"${chirrtl.main}.vfrag")))
    verilog.close
    result.circuit
  }

  def apply[T <: chisel3.Module](w: => T, dir: File): Circuit = {
    lazy val dut = w
    val chirrtl = firrtl.Parser.parse(chisel3.Driver.emit(() => dut))
    apply(chirrtl, dut.io, dir)
  }
}