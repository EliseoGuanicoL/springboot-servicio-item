package com.formacionbdi.springboot.app.item.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.formacionbdi.springboot.app.item.models.Item;
import com.formacionbdi.springboot.app.item.models.Producto;
import com.formacionbdi.springboot.app.item.models.service.ItemService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

@RefreshScope
@RestController
public class ItemController {
	
	private final Logger logger = LoggerFactory.getLogger(ItemController.class);
	
	@Autowired
	private CircuitBreakerFactory circuitBreakerFactory;
	
	@Autowired
	private Environment env;
	
	@Autowired
	@Qualifier("serviceFeign")
	private ItemService itemService;
	
	@Value("${configuracion.texto}")
	private String texto;
	
	@GetMapping("/listar")
	public List<Item> listar(@RequestParam(name = "nombre", required = false) String nombre, @RequestHeader(name = "token-request", required = false) String token){
		System.out.println(nombre);
		System.out.println(token);
		return itemService.findAll();
	}
	
	// Esta anotacion de Hystrix sirve para configurar la tolerancia a fallos, en caso de que falle el microservicio o el metodo, ejecutara el metodo secundario.
	// @HystrixCommand(fallbackMethod = "metodoAlternativo")
	@GetMapping("/ver/{id}/cantidad/{cantidad}")
	public Item detalle(@PathVariable Long id, @PathVariable Integer cantidad) {
		return circuitBreakerFactory.create("items")
				.run(() -> itemService.findById(id, cantidad), e -> metodoAlternativo(id, cantidad, e));
	}
	
	// Cuando tiene la anotacion, solo utiliza la config del application.yml o del properties
	@CircuitBreaker(name = "items", fallbackMethod = "metodoAlternativo")
	@GetMapping("/ver2/{id}/cantidad/{cantidad}")
	public Item detalle2(@PathVariable Long id, @PathVariable Integer cantidad) {
		return itemService.findById(id, cantidad);
	}
	
	// Devuelve una llamada asincronica, una llamada a futuro, maneja todo lo que es TIMEOUT(una clase de java 8)
	// No contabiliza el numero de peticiones, solo lanza el timeout, y no entra al corto circuito.
	@TimeLimiter(name = "items", fallbackMethod = "metodoAlternativo2")
	@GetMapping("/ver3/{id}/cantidad/{cantidad}")
	public CompletableFuture<Item> detalle3(@PathVariable Long id, @PathVariable Integer cantidad) {
		return CompletableFuture.supplyAsync(() -> itemService.findById(id, cantidad));
	}
	
	//Se pueden combinar
	@CircuitBreaker(name = "items", fallbackMethod = "metodoAlternativo2")
	@TimeLimiter(name = "items")
	@GetMapping("/ver4/{id}/cantidad/{cantidad}")
	public CompletableFuture<Item> detalle4(@PathVariable Long id, @PathVariable Integer cantidad) {
		return CompletableFuture.supplyAsync(() -> itemService.findById(id, cantidad));
	}
	
	@GetMapping("/obtener-config")
	public ResponseEntity<?> obtenerConfig(@Value("${server.port}") String port) {
		Map<String, String> json = new HashMap<>();
		json.put("texto", texto);
		json.put("puerto", port);
		
		if (env.getActiveProfiles().length>0 && env.getActiveProfiles()[0].equals("dev")) {
			json.put("autor.nombre", env.getProperty("configuracion.autor.nombre"));
			json.put("autor.email", env.getProperty("configuracion.autor.email"));
		}
		
		return new ResponseEntity<Map<String, String>>(json, HttpStatus.OK);
	}
	
	public Item metodoAlternativo(Long id, Integer cantidad, Throwable e) {
		logger.info(e.getMessage());
		Item item = new Item();
		Producto producto = new Producto();
		
		item.setCantidad(cantidad);
		producto.setId(id);
		producto.setNombre("Camara Sony");
		producto.setPrecio(500.00);
		item.setProducto(producto);
		return item;
	}
	
	public CompletableFuture<Item> metodoAlternativo2(Long id, Integer cantidad, Throwable e) {
		logger.info(e.getMessage());
		Item item = new Item();
		Producto producto = new Producto();
		
		item.setCantidad(cantidad);
		producto.setId(id);
		producto.setNombre("Camara Sony");
		producto.setPrecio(500.00);
		item.setProducto(producto);
		return CompletableFuture.supplyAsync(() ->  item);
	}

}
