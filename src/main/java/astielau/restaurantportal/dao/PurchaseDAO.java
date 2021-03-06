package astielau.restaurantportal.dao;

import astielau.restaurantportal.cpk.PurchasePK;
import astielau.restaurantportal.entities.ClientEntity;
import astielau.restaurantportal.entities.DishEntity;
import astielau.restaurantportal.entities.OrderItemEntity;
import astielau.restaurantportal.entities.PurchaseEntity;
import astielau.restaurantportal.wsclients.invoice.InvoiceDTO;
import astielau.restaurantportal.wsclients.invoice.InvoiceItemDTO;
import astielau.restaurantportal.wsclients.invoice.InvoiceRegistryWS;
import astielau.restaurantportal.wsclients.invoice.InvoiceRegistryWS_Service;
import java.util.GregorianCalendar;
import java.util.List;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.xml.datatype.DatatypeFactory;

@Stateless
public class PurchaseDAO {
    
    @PersistenceContext
    private EntityManager em;
    
    @EJB
    OrderItemDAO orderItemDAO;
    
    InvoiceRegistryWS invoiceRegistry;
    
    public PurchaseDAO(){
        InvoiceRegistryWS_Service invoiceRegistryWS = new InvoiceRegistryWS_Service();
        invoiceRegistry = invoiceRegistryWS.getInvoiceRegistryWSPort();
    }
    
    public List<PurchaseEntity> getAllPurchases(String username){
        try {
            return (List<PurchaseEntity>) em.createNamedQuery("findAllPurchases")
                                            .getResultList();
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: getAllPurchases");
            System.out.println( e.getMessage() );
        }
        return null;
    }
    
    public List<PurchaseEntity> getClientPurchases(String username){
        try {
            return (List<PurchaseEntity>) em.createNamedQuery("findClientPurchases")
                                            .setParameter("username", username)
                                            .getResultList();
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: getClientPurchases");
            System.out.println( e.getMessage() );
        }
        return null;
    }
    
    public PurchaseEntity getPurchase(String username, Long purchaseId){
        try {
            PurchasePK purchasePK = new PurchasePK(username, purchaseId);
            PurchaseEntity purchase = em.find(PurchaseEntity.class, purchasePK);
            return purchase;
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: getPurchase");
            System.out.println( e.getMessage() );
        }
        return null;
    }
    
    
    //ShoppingCart
    
    public PurchaseEntity getUserShoppingCart(String username){
        try {
            return (PurchaseEntity) em.createNamedQuery("findClientShoppingCart")
                                      .setParameter("username", username)
                                      .getSingleResult();
        } catch ( NoResultException e ) {
            System.out.println( e.getMessage() );
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: getUserShoppingCart");
            System.out.println( e.getMessage() );
        }
        return null;
    }
    
    public PurchaseEntity createShoppingCart(ClientEntity client){
        try {
            if(getUserShoppingCart(client.getName()) == null){ 
                PurchaseEntity purchase = new PurchaseEntity(client);
                em.persist(purchase);
                return purchase;
            } else {
                System.out.println("Uber Shit");
            }
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: createPurchase");
            System.out.println( e.getMessage() );
        }
        return null;
    }
    
    private void registerInvoice(PurchaseEntity purchase){
        try {
            InvoiceDTO invoice = new InvoiceDTO();
            invoice.setEmissorTaxId("321654987");
            invoice.setEmisorName("RESTaurantPortal");
            invoice.setClientTaxId(purchase.getClient().getTaxId()); 
            invoice.setVAT(23);
            invoice.setEmissionDate(DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar())); 
            List<InvoiceItemDTO> items = invoice.getItems();
            InvoiceItemDTO i;
            for(OrderItemEntity item: purchase.getItems()){
                i = new InvoiceItemDTO();
                i.setName(item.getDish().getName());
                i.setPrice(item.getDish().getPrice());
                i.setQuantity(item.getQuantity());
                items.add(i);
            }
            
            invoiceRegistry.registerInvoice(invoice);
            System.out.println("DONE");
            
        } catch ( Exception e ){
            System.out.println( e.getMessage() );
        }
    }
    
    public void checkout(String username){
        try {
            PurchaseEntity purchase = getUserShoppingCart(username);
            if(purchase != null){ 
                if(purchase.getTotal() > 0){
                    purchase.pay();
                    registerInvoice(purchase);
                }
            } else {
                //erro
            }
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: payPurchase");
            System.out.println( e.getMessage() );
        }
    }
    
    public void emptyShoppingCart(String username){
        try {
            PurchaseEntity purchase = getUserShoppingCart(username);
            if(purchase != null){ 
                for(OrderItemEntity item: purchase.getItems()){
                    em.remove(item);
                }
            } else {
                //erro
            }
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: addDishToShoppingCart");
            System.out.println( e.getMessage() );
        }
    }
    
    public void addDishToShoppingCart(String username, DishEntity dish, Integer quantity){
        try {
            if(quantity > 0){
                PurchaseEntity shoppingCart = getUserShoppingCart(username);
                OrderItemEntity orderItem = orderItemDAO.getOrderItem(shoppingCart.getClient().getUsername(), shoppingCart.getId(), dish.getRestaurant().getName(), dish.getId());
                if(orderItem == null){
                    orderItem = orderItemDAO.createOrderItem(shoppingCart, dish, quantity);
                    shoppingCart.addItem(orderItem);
                } else {
                    orderItem.setQuantity(orderItem.getQuantity() + quantity);
                }
            }
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: addDishToShoppingCart");
            System.out.println( e.getMessage() );
        }
    }
    
    public void removeDishFromShoppingCart(String username, DishEntity dish, Integer quantity){
        try {
            if(quantity > 0){
                PurchaseEntity shoppingCart = getUserShoppingCart(username);
                OrderItemEntity orderItem = orderItemDAO.getOrderItem(shoppingCart.getClient().getUsername(), shoppingCart.getId(), dish.getRestaurant().getName(), dish.getId());
                if(orderItem == null){
                    //erro
                } else {
                    System.out.println("Removing " + quantity + " " + dish.getName() + "s");
                    Integer total = orderItem.getQuantity() - quantity;
                    if(total < 0){
                        //erro
                    } else if(total == 0) {
                        orderItemDAO.deleteOrderItem(shoppingCart.getClient().getUsername(), shoppingCart.getId(), dish.getRestaurant().getName(), dish.getId());
                    } else {
                        orderItem.setQuantity(orderItem.getQuantity() - quantity);
                    }
                }
            }
        } catch ( Exception e) {
            System.out.println("Error @ PurchaseDAO: removeDishFromShoppingCart");
            System.out.println( e.getMessage() );
        }
    }
}
